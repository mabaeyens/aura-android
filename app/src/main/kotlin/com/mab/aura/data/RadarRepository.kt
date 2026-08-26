package com.mab.aura.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mab.aura.core.geo.RadarSite
import com.mab.aura.core.model.Location
import com.mab.aura.core.net.AemetClient
import com.mab.aura.store.SecretStore
import com.mab.aura.ui.cards.AuraRadarInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * Fetches the nearest regional radar frame for a location, cached on disk with a 10-minute TTL (the cadence
 * AEMET republishes regional radar). Android port of `RadarService.swift`.
 *
 * Separate from the coalesced forecast refresh in [WeatherRepository]: radar is lazy (only the "Hoy" screen
 * needs it) and its image bytes deliberately stay out of the cached [com.mab.aura.core.model.WeatherSnapshot]
 * — they'd bloat it. The raw frames live in the app's `cacheDir` (the OS purges it under pressure), not the
 * `filesDir` where the snapshot and key live. The AEMET key is read per call from [SecretStore], as
 * [WeatherRepository] does; a missing key or any fetch failure with no stale frame just returns null and the
 * radar card doesn't appear.
 */
class RadarRepository(context: Context) {

    private val appContext = context.applicationContext
    private val secretStore = SecretStore(appContext)

    /** Regional radar republishes every ~10 minutes; don't re-fetch within that window. */
    private val ttlMillis = 10 * 60 * 1000L

    private fun cacheFile(code: String): File = File(appContext.cacheDir, "radar-$code.img")

    /**
     * The nearest regional radar frame for [location], served from a ≤10-min disk cache or fetched fresh.
     * Returns null when there's no key, or the fetch fails with no cached frame to fall back on, or the bytes
     * don't decode — the radar card then simply doesn't appear. Mirrors `RadarService.frame(for:force:)`.
     */
    suspend fun frame(location: Location, force: Boolean = false): AuraRadarInfo? {
        val site = RadarSite.nearest(location.latitude, location.longitude)
        val file = cacheFile(site.code)

        if (!force) {
            cachedFrame(file, site, maxAgeMillis = ttlMillis)?.let { return it }
        }

        val key = secretStore.apiKey()
        if (key.isNullOrEmpty()) {
            // No key: any stale frame beats none (maxAge infinite), matching iOS's offline fallback.
            return cachedFrame(file, site, maxAgeMillis = Long.MAX_VALUE)
        }

        return try {
            val bytes = AemetClient(key).radarRegional(site.code)
            val image = decode(bytes) ?: return cachedFrame(file, site, maxAgeMillis = Long.MAX_VALUE)
            // Only persist bytes that actually decoded, so a corrupt payload never poisons the cache.
            runCatching { withContext(Dispatchers.IO) { file.writeBytes(bytes) } }
            AuraRadarInfo(image = image, siteName = site.name, time = Instant.now())
        } catch (_: Exception) {
            cachedFrame(file, site, maxAgeMillis = Long.MAX_VALUE)
        }
    }

    /**
     * Delete radar frames left in `cacheDir` older than [olderThanMillis] (default 24 h). The live TTL is 10
     * minutes, so anything this old is dead weight; one file accumulates per radar site the user has been
     * near. The OS already purges the cache under pressure — this just keeps it tidy between those. Direct
     * port of `RadarService.pruneCache`.
     */
    fun pruneCache(olderThanMillis: Long = 24 * 60 * 60 * 1000L) {
        val cutoff = Instant.now().toEpochMilli() - olderThanMillis
        appContext.cacheDir.listFiles()
            ?.filter { it.name.startsWith("radar-") && it.lastModified() < cutoff }
            ?.forEach { runCatching { it.delete() } }
    }

    /** The cached frame at [file] if it exists, is younger than [maxAgeMillis], and still decodes. */
    private fun cachedFrame(file: File, site: RadarSite, maxAgeMillis: Long): AuraRadarInfo? {
        if (!file.exists()) return null
        val modified = file.lastModified()
        val ageOk = maxAgeMillis == Long.MAX_VALUE || Instant.now().toEpochMilli() - modified < maxAgeMillis
        if (!ageOk) return null
        val image = decode(file.readBytes()) ?: return null
        return AuraRadarInfo(image = image, siteName = site.name, time = Instant.ofEpochMilli(modified))
    }

    /** Decode radar image bytes (AEMET serves GIF/PNG) to a Compose [ImageBitmap], or null if they don't. */
    private fun decode(bytes: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}
