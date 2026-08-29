package com.mab.aura.data

import android.content.Context
import com.mab.aura.core.model.ForecastBulletin
import com.mab.aura.core.model.MedioPlazoForecast
import com.mab.aura.core.net.AemetBulletinParser
import com.mab.aura.core.net.AemetClient
import com.mab.aura.core.net.nationalMedioPlazo
import com.mab.aura.core.net.nationalManana
import com.mab.aura.core.net.nationalPasadoManana
import com.mab.aura.core.net.nationalToday
import com.mab.aura.store.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetches AEMET's national text forecast for the "Predicción nacional" card, minding a strict request budget.
 * A close sibling of [SurfaceAnalysisRepository]: self-contained, off the forecast path, key from [SecretStore],
 * and "no key or any failure with no cached copy just returns null and the card disappears".
 *
 * The budget is the point (four national products, refreshed often, would dwarf the rest of the app's calls),
 * so the two halves are gated differently:
 * - **[today]** rides the refresh path, so it is gated to at most once per 6 h via a disk cache — the same
 *   file-age TTL trick surface uses, not a separate marker. Within the window it re-parses the cached text and
 *   makes no network call; a cold start reads the last cache. It also survives process death, so reopening the
 *   app shows today's national line straight away.
 * - **[manana]/[pasadoManana]/[medioPlazo]** power the detail sheet's other three segments and fetch **lazily,
 *   only when that segment is opened**, each cached in memory (mutex-guarded so two quick opens can't double
 *   fetch). Never opening the sheet costs zero calls for these three — the whole reason they're split out.
 */
class NationalForecastRepository(context: Context) {

    private val appContext = context.applicationContext
    private val secretStore = SecretStore(appContext)

    /** AEMET amends the national products through the day, but 6 h between our own fetches is plenty. */
    private val ttlMillis = 6 * 60 * 60 * 1000L

    /** The resolved "today" bulletin's raw text, cached on disk so the 6 h gate survives a process restart. */
    private val todayFile: File get() = File(appContext.cacheDir, "national-today.txt")

    // In-memory caches for the three lazy sheet segments. Each holds its last good value and when it was
    // fetched; a Mutex serializes concurrent opens so a burst of taps fires at most one network call.
    private val mananaCache = MemoryCache<ForecastBulletin>(ttlMillis)
    private val pasadoCache = MemoryCache<ForecastBulletin>(ttlMillis)
    private val medioCache = MemoryCache<MedioPlazoForecast>(ttlMillis)

    /**
     * Today's national narrative, served from the ≤6 h disk cache or fetched fresh. Returns null when there's
     * no key, or the fetch fails with nothing cached to fall back on, or the text doesn't parse — the card then
     * simply doesn't appear. [force] (pull-to-refresh) bypasses the age gate.
     */
    suspend fun today(force: Boolean = false): ForecastBulletin? {
        if (!force) {
            cachedToday(maxAgeMillis = ttlMillis)?.let { return it }
        }

        val key = secretStore.apiKey()
        if (key.isNullOrEmpty()) {
            // No key: any cached copy beats none, matching surface's offline fallback.
            return cachedToday(maxAgeMillis = Long.MAX_VALUE)
        }

        return try {
            val resolved = AemetClient(key).nationalToday() ?: return cachedToday(maxAgeMillis = Long.MAX_VALUE)
            // Only persist text that actually parsed into a bulletin, so a bad payload never poisons the cache.
            runCatching { withContext(Dispatchers.IO) { todayFile.writeText(resolved.raw) } }
            resolved.bulletin
        } catch (_: Exception) {
            cachedToday(maxAgeMillis = Long.MAX_VALUE)
        }
    }

    /** The `manana` segment (valid for tomorrow), fetched lazily and cached; null on failure with no cache. */
    suspend fun manana(): ForecastBulletin? =
        mananaCache.get { withClient { it.nationalManana() } }

    /** The `pasadomanana` segment (the day after), fetched lazily and cached; null on failure with no cache. */
    suspend fun pasadoManana(): ForecastBulletin? =
        pasadoCache.get { withClient { it.nationalPasadoManana() } }

    /** The `medioplazo` segment (the days beyond), fetched lazily and cached; null on failure with no cache. */
    suspend fun medioPlazo(): MedioPlazoForecast? =
        medioCache.get { withClient { it.nationalMedioPlazo() } }

    /** Run [block] against a keyed client, or return null when there's no key (the segment shows unavailable). */
    private suspend fun <T> withClient(block: suspend (AemetClient) -> T?): T? {
        val key = secretStore.apiKey()
        if (key.isNullOrEmpty()) return null
        return runCatching { block(AemetClient(key)) }.getOrNull()
    }

    /** The cached "today" text if it exists, is younger than [maxAgeMillis], and still parses. */
    private suspend fun cachedToday(maxAgeMillis: Long): ForecastBulletin? {
        val file = todayFile
        if (!file.exists()) return null
        val ageOk = maxAgeMillis == Long.MAX_VALUE ||
            System.currentTimeMillis() - file.lastModified() < maxAgeMillis
        if (!ageOk) return null
        val text = runCatching { withContext(Dispatchers.IO) { file.readText() } }.getOrNull() ?: return null
        return AemetBulletinParser.parse(text)
    }

    /**
     * A one-value in-memory cache with a TTL and a mutex. [get] returns the cached value while it's fresh, else
     * runs [fetch]; on a fetch failure it falls back to any previously cached (now stale) value. The lock means
     * two coroutines opening the same segment at once share one fetch instead of firing two.
     */
    private class MemoryCache<T>(private val ttlMillis: Long) {
        private val mutex = Mutex()
        private var value: T? = null
        private var fetchedAt = 0L

        suspend fun get(fetch: suspend () -> T?): T? = mutex.withLock {
            val now = System.currentTimeMillis()
            value?.let { if (now - fetchedAt < ttlMillis) return it }
            val fresh = fetch()
            if (fresh != null) {
                value = fresh
                fetchedAt = now
            }
            fresh ?: value
        }
    }
}
