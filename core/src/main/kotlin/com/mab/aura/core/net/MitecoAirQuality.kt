package com.mab.aura.core.net

import com.mab.aura.core.air.AirComponent
import com.mab.aura.core.air.AirQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nearest-station air quality from Spain's MITECO ICA feed. The national last-hour CSV
 * (`ica-ultima-hora.csv`) gives every active station's overall índice (1–6); an undocumented backend
 * (`sql1` query) fills in the per-pollutant breakdown the CSV lacks. Nearest is resolved locally per
 * location, so the whole feed is fetched once.
 *
 * Direct port of the `MitecoAirQuality` half of `AirQuality.swift` (the [AirComponent]/[AirQuality] model
 * halves are already in `:core`). Swift models this as a namespacing `enum` with `static` methods and an
 * injected `URLSession`; here the pure helpers live on the [companion object] (so call sites and tests
 * read the same — `MitecoAirQuality.parse(csv)`), and the three network methods are instance methods whose
 * [feedUrl]/[backendUrl]/[httpClient] are injectable for a `MockWebServer`, mirroring [OpenMeteoUV]. Like
 * [OpenMeteoUV] and unlike [AemetClient] this never throws: any failure yields an empty result, so a
 * MITECO outage just hides the air-quality card and never blocks the AEMET refresh.
 */
class MitecoAirQuality(
    private val feedUrl: String = FEED_URL,
    private val backendUrl: String = BACKEND_URL,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** One parsed row of the national feed (only active stations with a real category are kept). */
    data class Station(
        /** `cod_estacion` — keys the backend per-pollutant query. */
        val code: Int,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        /** Raw feed code: 1–6, or category × 10 (partial); never 0 here (0 rows are dropped). */
        val indice: Int,
        val pollutant: String?,
        val measured: Instant,
    )

    /** One station's latest ICA value for a pollutant, with the reading's UTC hour. */
    data class ReadingValue(val value: Double, val date: Instant)

    /**
     * Fetch the whole national feed once (nearest is resolved locally per location). Empty — never throws —
     * if the feed is unreachable or non-200.
     */
    suspend fun stations(): List<Station> {
        val body = try {
            withContext(Dispatchers.IO) {
                httpClient.newCall(Request.Builder().url(feedUrl).build()).execute().use { response ->
                    if (response.code == 200) response.body?.string() else null
                }
            }
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return parse(body)
    }

    /**
     * The per-pollutant breakdown for a location: each pollutant taken from the nearest active station that
     * measures it. The single nearest station (often urban-traffic) usually reports NO₂ and particulates but
     * not O₃ (background stations) or SO₂ (industrial), so stations are probed nearest-first, one `sql1` POST
     * each, until every pollutant is found or the search gives up ([maxStations] probed, or the next station
     * past [maxRadiusKm]). Each [AirComponent] carries its own station, distance and time. Never throws.
     */
    suspend fun breakdown(
        latitude: Double,
        longitude: Double,
        stations: List<Station>,
        maxStations: Int = 10,
        maxRadiusKm: Double = 80.0,
    ): List<AirComponent> {
        val sorted = stations
            .map { it to haversine(latitude, longitude, it.latitude, it.longitude) }
            .sortedBy { it.second }
        val needed = POLLUTANT_ORDER.toSet()
        val found = HashMap<String, AirComponent>()
        var probed = 0
        // Sequential on purpose: it breaks as soon as every pollutant is found, so the nearest stations that
        // already cover everything are the only ones queried. Parallelising would defeat that early-out.
        for ((station, km) in sorted) {
            if (found.size == needed.size || probed >= maxStations || km > maxRadiusKm) break
            probed += 1
            val readings = stationReadings(station)
            for ((magnitud, reading) in readings) {
                if (magnitud in needed && found[magnitud] == null) {
                    found[magnitud] = AirComponent(
                        pollutant = magnitud, value = reading.value,
                        station = prettyName(station.name), distanceKm = km, measured = reading.date,
                    )
                }
            }
        }
        return POLLUTANT_ORDER.mapNotNull { found[it] }
    }

    /** One station's latest ICA value per pollutant, via a single `sql1` POST over its UTC day. Empty — never throws — on any failure. */
    private suspend fun stationReadings(station: Station): Map<String, ReadingValue> {
        val day = UTC_DAY.format(station.measured)
        val body = requestBody(station.code, day)
        val payload = try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(backendUrl)
                    .post(body.toRequestBody(FORM_URLENCODED))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 200) response.body?.string() else null
                }
            }
        } catch (_: Exception) {
            null
        } ?: return emptyMap()
        return latestICAValues(payload, day)
    }

    /** One hourly row from `sql1`. `valorMedido` is the raw hourly concentration; `valorMediaMovil` is the
     * running mean the ICA is built from (8 h for O₃, 24 h for PM; null for NO₂/SO₂). The `datoMedido`
     * flags validate each; an unmeasured pollutant comes back all-null. */
    @Serializable
    private data class Reading(
        val hora: Int,
        val magnitud: String,
        @SerialName("valor_medido") val valorMedido: Double? = null,
        @SerialName("dato_medido") val datoMedido: Boolean,
        @SerialName("valor_media_movil") val valorMediaMovil: Double? = null,
        @SerialName("dato_medido_mm") val datoMedidoMm: Boolean? = null,
    )

    companion object {
        const val FEED_URL = "https://ica.miteco.es/datos/ica-ultima-hora.csv"

        /** The undocumented ICA backend that serves the per-pollutant breakdown the CSV lacks. */
        const val BACKEND_URL = "https://backend.ica.miteco.es/sgca/"

        // The canonical pollutant order (mirrors AirComponent's private `order`, which also drives its rank
        // sort): NO₂, O₃, PM2.5, PM10, SO₂. Drives `breakdown`'s "needed" set and its output ordering.
        private val POLLUTANT_ORDER = listOf("NO2", "O3", "PM2.5", "PM10", "SO2")

        private val FORM_URLENCODED = "application/x-www-form-urlencoded".toMediaType()
        private val SPAIN: Locale = Locale.forLanguageTag("es-ES")

        // Shared decoder for the `sql1` payload (a bare array, no envelope). Kept here rather than reusing
        // the instance `json`, because `latestICAValues`/`parseComponents` are pure static helpers.
        private val JSON = Json { ignoreUnknownKeys = true }

        // MITECO stamps everything in UTC. `UTC_DAY` formats an instant as the backend's "yyyyMMdd" query
        // day; the CSV `fecha` is a plain local-looking "yyyy-MM-dd'T'HH:mm:ss" that is really UTC.
        private val UTC_DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US).withZone(ZoneOffset.UTC)

        // --- CSV feed ---------------------------------------------------------------------------------

        /**
         * Parse the national feed. Columns: cod_estacion,nombre,tipo,latitud,longitud,activa,fecha,indice,
         * debido_a (UTF-8, comma). Drops inactive stations and índice 0 (no data). `debido_a` is a bare token
         * in the last-hour feed (a quoted list only appears in forecast files), so a naïve comma split is safe.
         */
        fun parse(csv: String): List<Station> {
            val out = ArrayList<Station>()
            // Swift splits on `isNewline` (dropping empty subsequences), then drops the header row. Splitting
            // on both \n and \r turns a \r\n into an empty field, which the isNotEmpty filter removes — same net.
            val rows = csv.split('\n', '\r').filter { it.isNotEmpty() }.drop(1)
            for (line in rows) {
                // Keep empty fields (Kotlin's split does by default), so column indices stay stable.
                val f = line.split(",")
                if (f.size < 9 || f[5] != "true") continue
                val code = f[0].toIntOrNull() ?: continue
                val lat = f[3].toDoubleOrNull() ?: continue
                val lon = f[4].toDoubleOrNull() ?: continue
                val indice = f[7].toIntOrNull() ?: continue
                if (indice == 0) continue
                val poll = f[8].trim('"', ' ')
                out.add(
                    Station(
                        code = code, name = f[1], latitude = lat, longitude = lon, indice = indice,
                        pollutant = poll.ifEmpty { null }, measured = parseDate(f[6]) ?: Instant.now(),
                    ),
                )
            }
            return out
        }

        /** The nearest active station to a point, and its distance (km), or null if none is usable. */
        internal fun nearestStation(
            latitude: Double, longitude: Double, stations: List<Station>,
        ): Pair<Station, Double>? {
            var best: Pair<Station, Double>? = null
            for (s in stations) {
                val km = haversine(latitude, longitude, s.latitude, s.longitude)
                if (best == null || km < best.second) best = s to km
            }
            return best
        }

        /** The nearest active station to a point, as an [AirQuality], or null if none is usable. */
        fun nearest(latitude: Double, longitude: Double, stations: List<Station>): AirQuality? {
            val (s, km) = nearestStation(latitude, longitude, stations) ?: return null
            val partial = s.indice >= 10
            val category = if (partial) s.indice / 10 else s.indice
            if (category !in 1..6) return null
            return AirQuality.create(
                category = category, partial = partial, pollutant = s.pollutant,
                station = prettyName(s.name), distanceKm = km, measured = s.measured,
            )
        }

        /**
         * The composite ICA for a location from a per-pollutant [breakdown]: the índice is the worst
         * pollutant's band (MITECO's "peor contaminante" rule), computed from each pollutant's own
         * nearest-station value. That worst pollutant drives it, and its station/distance become the
         * headline's. Null for an empty breakdown, so the caller can fall back to [nearest].
         */
        fun composite(components: List<AirComponent>): AirQuality? {
            val ranked = components.filter { it.icaCategory >= 1 }
            // The driver is the highest ICA band; ties go to the nearer station (smaller distance). The
            // descending distance key makes the smaller distance compare greater, so `maxWith` picks it.
            val driver = ranked.maxWithOrNull(
                compareBy<AirComponent> { it.icaCategory }
                    .thenByDescending { it.distanceKm ?: Double.POSITIVE_INFINITY },
            ) ?: return null
            val measured = components.mapNotNull { it.measured }.maxOrNull() ?: Instant.now()
            return AirQuality.create(
                category = driver.icaCategory, partial = false, pollutant = driver.pollutant,
                station = driver.station ?: "", distanceKm = driver.distanceKm ?: 0.0,
                measured = measured, components = components,
            )
        }

        // --- Backend `sql1` query --------------------------------------------------------------------

        /**
         * The `application/x-www-form-urlencoded` body for the `sql1` query. Only the value is percent-encoded
         * (the '#', space and ':' must be escaped); the "sql=" separator stays literal — encoding it too turns
         * '=' into %3D, and the backend then sees no parameter and answers "Consulta incorrecta".
         */
        fun requestBody(code: Int, day: String): ByteArray {
            val sql = "sql1#$code#$day 00:00#$day 23:00"
            return "sql=${percentEncodeAlphanumerics(sql)}".toByteArray(Charsets.UTF_8)
        }

        /**
         * The latest validated ICA value per pollutant: the running mean (`valor_media_movil`) when the
         * backend supplies one — the value the índice is actually built from — otherwise the last valid hourly
         * `valor_medido` (NO₂/SO₂ carry no average). Unmeasured pollutants (all-null) are omitted, so there are
         * never fabricated zeros. Keyed by magnitud, with the reading's UTC hour.
         */
        internal fun latestICAValues(payload: String, day: String): Map<String, ReadingValue> {
            val rows = try {
                JSON.decodeFromString(ListSerializer(Reading.serializer()), payload)
            } catch (_: Exception) {
                return emptyMap()
            }
            val latest = HashMap<String, Pair<Int, Double>>()   // magnitud -> (hora, value)
            for (r in rows) {
                val icaValue: Double? = when {
                    r.valorMediaMovil != null && r.datoMedidoMm == true -> r.valorMediaMovil
                    r.valorMedido != null && r.datoMedido -> r.valorMedido
                    else -> null
                }
                if (icaValue == null) continue
                val existing = latest[r.magnitud]
                if (existing != null && existing.first >= r.hora) continue
                latest[r.magnitud] = r.hora to icaValue
            }
            return latest.mapValues { (_, hv) -> ReadingValue(hv.second, hourDate(day, hv.first)) }
        }

        /**
         * The bare per-pollutant breakdown for one payload (no source), for the parser tests and any
         * single-station use; [breakdown] is the app path. Uses today's UTC day only to stamp the
         * (discarded) hour.
         */
        fun parseComponents(payload: String): List<AirComponent> =
            latestICAValues(payload, UTC_DAY.format(Instant.now()))
                .map { (magnitud, rv) -> AirComponent(pollutant = magnitud, value = rv.value) }

        /** An [Instant] for the reading's UTC hour on the query day (`day` is "yyyyMMdd", UTC). */
        private fun hourDate(day: String, hour: Int): Instant {
            val base = try {
                LocalDate.parse(day, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant()
            } catch (_: Exception) {
                Instant.now()
            }
            return base.plusSeconds(hour.toLong() * 3600)
        }

        // The CSV `fecha` shape, e.g. "2026-08-21T08:00:00" — no offset, but the feed is UTC.
        private fun parseDate(s: String): Instant? =
            try {
                LocalDateTime.parse(s).toInstant(ZoneOffset.UTC)
            } catch (_: Exception) {
                null
            }

        /** Feed station names are ALL CAPS ("PLAZA DEL CARMEN"); title-case them for display. */
        private fun prettyName(raw: String): String =
            raw.split(" ").joinToString(" ") { word ->
                word.lowercase(SPAIN).replaceFirstChar { it.uppercase() }
            }

        // Percent-encode allowing only ASCII alphanumerics — the exact set Swift's
        // `addingPercentEncoding(withAllowedCharacters: .alphanumerics)` permits, so '#', space and ':' all
        // become %23/%20/%3A. java.net.URLEncoder can't be used: it keeps -_.* and encodes space as '+'.
        private fun percentEncodeAlphanumerics(s: String): String {
            val sb = StringBuilder()
            for (byte in s.toByteArray(Charsets.UTF_8)) {
                val c = (byte.toInt() and 0xFF).toChar()
                if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9') {
                    sb.append(c)
                } else {
                    sb.append('%').append(String.format(Locale.US, "%02X", byte.toInt() and 0xFF))
                }
            }
            return sb.toString()
        }

        /** Great-circle distance in km (haversine). */
        private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val p = Math.PI / 180
            val dLat = (lat2 - lat1) * p
            val dLon = (lon2 - lon1) * p
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * p) * cos(lat2 * p) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * asin(min(1.0, sqrt(a)))
        }
    }
}
