package com.mab.aura.data

import android.content.Context
import com.mab.aura.core.air.AirQuality
import com.mab.aura.core.geo.comunidad
import com.mab.aura.core.model.AvisoArea
import com.mab.aura.core.model.ForecastBulletin
import com.mab.aura.core.model.Location
import com.mab.aura.core.model.WeatherAlert
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.model.make
import com.mab.aura.core.model.nearest
import com.mab.aura.core.model.topActive
import com.mab.aura.core.net.AemetClient
import com.mab.aura.core.net.AemetClientException
import com.mab.aura.core.net.comunidadBulletin
import com.mab.aura.core.net.MitecoAirQuality
import com.mab.aura.core.net.OpenMeteoUV
import com.mab.aura.core.uv.UVIndex
import com.mab.aura.store.SecretStore
import com.mab.aura.store.Settings
import com.mab.aura.store.SnapshotCache
import androidx.glance.appwidget.updateAll
import com.mab.aura.widget.AuraGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant

/**
 * The app's weather repository: the orchestration layer that ties the `:core` network clients (Layer C) to
 * the on-device snapshot cache (Layer D). Android port of `AEMETService.swift`.
 *
 * There is one fetch path, [refresh], and it is *coalesced*: if a refresh is already running, every other
 * caller awaits that same run instead of starting its own, so overlapping triggers (a cold start plus the
 * screen appearing) never fan out into duplicate AEMET request bursts that would trip the rate limit. AEMET
 * has no bulk municipal-forecast endpoint, so each location is still its own call; the shared national feeds
 * (air quality, UV cities, avisos) are fetched once per refresh and sliced locally.
 *
 * The only remaining divergence from the Swift is the surfaces Android doesn't have: there is no Watch reload
 * or notification step (no watch surface on Android). Everything else — the prune, the one-hour staleness skip,
 * the community bulletin behind the "Predicción" card (`comunidadBulletin`, one fetch per autonomous community),
 * the nearest-station observation (`observacionTodas`), the per-source composition into [WeatherSnapshot.make]
 * — is faithful.
 */
class WeatherRepository(context: Context) {

    private val appContext = context.applicationContext
    private val secretStore = SecretStore(appContext)
    private val snapshotCache = SnapshotCache(appContext)
    private val settings = Settings(appContext)

    // The MITECO air-quality and Open-Meteo UV fetchers are instances (they hold an HTTP client), unlike the
    // AEMET client which is rebuilt per refresh from the stored key. Their pure helpers (composite/nearest)
    // stay static on their companions.
    private val miteco = MitecoAirQuality()
    private val openMeteoUv = OpenMeteoUV()

    // A private scope so the coalesced refresh runs to completion independent of any one caller's lifecycle
    // (the Swift used a detached Task for the same reason); a cancelled screen must not cancel a refresh
    // other callers are awaiting.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val gate = Mutex()
    private var current: Deferred<String?>? = null

    /** Whether an AEMET key has been stored. The UI uses this to show a "add your key" state. */
    fun hasApiKey(): Boolean = !secretStore.apiKey().isNullOrEmpty()

    /** Store (or replace) the AEMET key. */
    fun setApiKey(key: String) = secretStore.setApiKey(key)

    /** The cached snapshot for one location, if any (the offline/last-known value). */
    suspend fun cachedSnapshot(ine: String): WeatherSnapshot? = snapshotCache.snapshot(ine)

    /**
     * Refresh and cache a snapshot for every [locations] entry, coalesced (see the type note). Unless [force],
     * skips locations cached within the last hour to stay well under AEMET's rate limit. Returns a Spanish
     * message if the refresh hit a problem worth showing (rate-limited, offline, …), else null.
     */
    suspend fun refresh(locations: List<Location>, force: Boolean = false): String? {
        val job = gate.withLock {
            current ?: scope.async {
                try {
                    performRefresh(locations, force)
                } finally {
                    gate.withLock { current = null }
                }
            }.also { current = it }
        }
        return job.await()
    }

    private fun client(): AemetClient? {
        val key = secretStore.apiKey()
        return if (key.isNullOrEmpty()) null else AemetClient(key)
    }

    private suspend fun performRefresh(locations: List<Location>, force: Boolean): String? {
        // Drop cached snapshots for locations the user no longer tracks (and any long-stale leftover) so the
        // cache stays bounded. Runs before the early-outs so removed favourites are cleaned up even when
        // nothing needs fetching.
        snapshotCache.prune(keepINEs = locations.map { it.ine }.toSet())

        val client = client() ?: return null
        var firstError: String? = null
        fun note(error: Throwable) { if (firstError == null) firstError = messageFor(error) }

        // Locations that need fetching: everything on `force`, otherwise those older than an hour or cached by
        // a build before the daily sky fields existed (their days decode with sky == null, which rendered every
        // day as a generic cloud until the next refresh).
        val now = Instant.now()
        val stale = if (force) locations else locations.filter { location ->
            val existing = snapshotCache.snapshot(location.ine) ?: return@filter true
            val ageMillis = now.toEpochMilli() - existing.updated.toEpochMilli()
            when {
                ageMillis >= ONE_HOUR_MILLIS -> true
                existing.days.isNotEmpty() && existing.days.all { it.sky == null } -> true
                else -> false
            }
        }
        if (stale.isEmpty()) return null

        // Air quality comes from MITECO's national ICA feed (not AEMET), one download for every location. It
        // never blocks the AEMET refresh — an outage just leaves the air card hidden.
        val airStations = runCatching { miteco.stations() }.getOrDefault(emptyList())

        // Today's forecast max UV — one AEMET call lists every provincial capital; resolved per location by
        // INE. A failure just leaves the UV card hidden.
        val uvCities = runCatching { client.uviCities(dia = 0) }.getOrElse { note(it); emptyList() }

        // Every recent surface observation from AEMET's conventional network, one call for every location;
        // resolved per location to the nearest recent station below. A failure just leaves the station card
        // (and the observed temperature) hidden.
        //
        // That national product updates once per hour, so gate the call on the stored measurement time
        // ([observationDue]): a refresh triggered by any location crossing the 1-hour write-time gate must
        // not re-download a reading that hasn't changed yet. When we skip, each location carries its last
        // good station reading forward via [WeatherSnapshot.make]'s previousObserved, so the observed card
        // holds rather than blanking. Persist the freshest record's time after a successful fetch. Scope is
        // observations only; the forecast, UV and avisos keep their own cadence (a warning is never
        // stale-cached). Ported from AEMETService.swift's observation clock.
        val observations = if (observationDue(settings.lastObservationFint.first(), now, force)) {
            runCatching { client.observacionTodas() }
                .onSuccess { obs ->
                    obs.mapNotNull { it.timestamp }.maxOrNull()?.let { settings.setLastObservationFint(it) }
                }
                .getOrElse { note(it); emptyList() }
        } else {
            emptyList()
        }

        // Fetch each distinct avisos area at most once, then resolve per location by province.
        val areas = stale.mapNotNull { AvisoArea.forProvincia(it.provinciaCode) }.toSet()
        val alertsByArea = HashMap<String, List<WeatherAlert>>()
        for (area in areas) {
            alertsByArea[area] = runCatching { client.avisos(area) }.getOrElse { note(it); emptyList() }
        }

        // The community narrative bulletin ("Predicción" card) is one text per autonomous community, so fetch
        // each distinct community at most once and resolve per location below (many municipalities share one).
        // A failure just leaves the bulletin card hidden; it never blocks the rest of the refresh.
        val comunidades = stale.mapNotNull { it.comunidad }.toSet()
        val bulletinByComunidad = HashMap<String, ForecastBulletin>()
        for (comunidad in comunidades) {
            runCatching { client.comunidadBulletin(comunidad, now) }
                .onSuccess { bulletinByComunidad[comunidad.code] = it }
                .onFailure { note(it) }
        }

        for (location in stale) {
            val daily = runCatching { client.municipioDiaria(location.ine) }.getOrElse { note(it); continue }
            val hourly = runCatching { client.municipioHoraria(location.ine) }.getOrNull()

            // Air quality: compose the índice from the worst pollutant across nearby stations (MITECO's own
            // method); on a miss, fall back to the single nearest station's published índice.
            val breakdown = runCatching {
                miteco.breakdown(location.latitude, location.longitude, airStations)
            }.getOrDefault(emptyList())
            val airQuality: AirQuality? = MitecoAirQuality.composite(breakdown)
                ?: MitecoAirQuality.nearest(location.latitude, location.longitude, airStations)

            // The nearest recent station to this location (freshest per station, within 3 h and 35 km), or
            // null when none qualifies — then the station card and observed temperature stay hidden.
            val observed = observations.nearest(to = location)

            // The still-cached snapshot, so a cycle that skipped the hourly observation fetch (see above)
            // keeps this location's last good station reading instead of blanking the observed card. Only
            // used as a fallback in make() when [observed] is null; a fresh reading always wins.
            val previous = snapshotCache.snapshot(location.ine)

            val uvIndex = UVIndex.pick(location.ine, uvCities)
            // Hourly UV from CAMS via Open-Meteo — the per-hour granularity AEMET doesn't publish. Never
            // blocks; an empty result just hides the hourly curve.
            val uvHourly = runCatching { openMeteoUv.fetch(location.latitude, location.longitude) }
                .getOrDefault(emptyList())

            val alert = AvisoArea.forProvincia(location.provinciaCode)
                ?.let { alertsByArea[it] }
                ?.topActive(location.provinciaCode)

            val bulletin = location.comunidad?.let { bulletinByComunidad[it.code] }

            val snapshot = WeatherSnapshot.make(
                location = location,
                daily = daily,
                hourly = hourly,
                observed = observed,
                previousObserved = previous,
                alert = alert,
                airQuality = airQuality,
                uvIndex = uvIndex,
                uvHourly = uvHourly,
                bulletin = bulletin,
            )
            // Don't let a thin snapshot overwrite a good one already cached for this location. The hourly
            // carry-forward in make() already covers a wholly-absent feed (hourly null); this catches the
            // other thin path — a fetch that succeeded but returned an empty/degenerate feed with no
            // resolvable current hour, which carry-forward (gated on hourly null) skips. A real location
            // switch or first-ever fetch, where the cache is null or itself thin, still writes.
            if (snapshot.hasCurrentHourData || previous?.hasCurrentHourData != true) {
                snapshotCache.upsert(snapshot)
            }
        }

        // Fresh data is in the cache — re-render every Home Screen widget so it doesn't wait for its own slow
        // system refresh. The widget reads the cache itself; this just tells it to. Matches iOS reloading the
        // widget timelines after a fetch. Never let a widget hiccup fail the refresh.
        runCatching { AuraGlanceWidget().updateAll(appContext) }

        return firstError
    }

    /** Spanish message for any error surfaced while talking to AEMET. Direct port of `AEMETService.message`. */
    private fun messageFor(error: Throwable): String = when (error) {
        is AemetClientException.MissingApiKey -> "Falta la clave de AEMET. Añádela en Ajustes."
        is AemetClientException.RateLimited -> "AEMET ha limitado las peticiones. Inténtalo en un minuto."
        is AemetClientException.Http -> "Error de red (HTTP ${error.code})."
        is AemetClientException.AemetStatus -> "AEMET devolvió ${error.estado}: ${error.descripcion}"
        is AemetClientException.Decoding -> "No se pudieron leer los datos de AEMET."
        is IOException -> "Sin conexión. Se muestran los últimos datos disponibles."
        else -> "No se pudo obtener la información."
    }

    private companion object {
        const val ONE_HOUR_MILLIS = 3_600_000L
    }
}

/** AEMET's hourly observation cadence (60 min) plus a 30-min publish-lag margin. AEMET publishes each
 *  reading 20-40 min after the hour, so a bare +60 would re-download the same `fint` and waste the call. */
private const val OBSERVATION_TTL_MILLIS = 90 * 60 * 1000L

/**
 * Whether the national observation feed ([com.mab.aura.core.net.AemetClient.observacionTodas]) is due for a
 * re-fetch. That product updates once per hour and each record carries its own measurement time; [anchor] is
 * the freshest such time from the last successful fetch (persisted in [Settings.lastObservationFint]). Hold
 * the last-known feed until `anchor + 90min` ([OBSERVATION_TTL_MILLIS]) — the hourly cadence plus a publish
 * lag — and skip the call until then. A [force] refresh always fetches; a missing [anchor] fetches; a
 * future-dated [anchor] is clamped to [now] so a bad timestamp can suppress the fetch for at most the margin
 * window, never indefinitely. Pure so it is unit-tested directly.
 *
 * Ported from `AEMETService.swift`'s observation clock. iOS also always-fetches on a single-location manual
 * refresh (`onlyINE`); Android has no manual/pull-to-refresh yet, so [force] is the only always-fetch
 * trigger. The normal passive load passes one location with `force = false`, so it is gated like any other.
 */
internal fun observationDue(anchor: Instant?, now: Instant, force: Boolean): Boolean {
    if (force || anchor == null) return true
    val clamped = if (anchor.isAfter(now)) now else anchor
    return !now.isBefore(clamped.plusMillis(OBSERVATION_TTL_MILLIS))
}
