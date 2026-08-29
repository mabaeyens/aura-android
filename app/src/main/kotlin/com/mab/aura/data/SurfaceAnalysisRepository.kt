package com.mab.aura.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mab.aura.core.net.AemetClient
import com.mab.aura.store.SecretStore
import com.mab.aura.ui.cards.AuraSurfaceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Fetches AEMET's surface analysis chart (the synoptic isobars-and-fronts map), cached on disk with a
 * 12-hour TTL — the cadence AEMET republishes it. Android port of the iOS `SurfaceAnalysisService`, and a
 * close sibling of [RadarRepository]: same lazy, off-the-forecast-path fetch, same "image bytes stay out of
 * the cached [com.mab.aura.core.model.WeatherSnapshot]" rule, same `cacheDir` home (the OS purges it under
 * pressure) and same "no key or any failure with no stale frame just returns null and the card disappears".
 *
 * Two things differ from radar, both from the map's shape and cadence:
 * - **The 12h gate.** Radar re-fetches whenever its single per-site file ages past 10 min. Surface files are
 *   named by *issue slot* (so a future short history is trivial — the iOS decision), so the gate is the
 *   newest `surface-*.img` file's age, not one fixed filename's.
 * - **Rotation.** AEMET ships the analysis as a 1400×2000 portrait GIF stored rotated (title runs up the
 *   right edge). We rotate it 90° clockwise once at decode into a wide ~2000×1400 landscape bitmap, so
 *   everything downstream (the card, the zoom) works with a plain, upright image — never a live rotation.
 */
class SurfaceAnalysisRepository(context: Context) {

    private val appContext = context.applicationContext
    private val secretStore = SecretStore(appContext)

    /** AEMET reissues the surface analysis every 12 h; don't re-fetch within that window. */
    private val ttlMillis = 12 * 60 * 60 * 1000L

    /** Cache files are `surface-<yyyyMMddHH>.img`, keyed by the map's nominal 00/12 UTC issue slot. */
    private val filePrefix = "surface-"
    private val slotFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC)

    /**
     * The latest surface analysis map, served from a ≤12-h disk cache or fetched fresh. Returns null when
     * there's no key, or the fetch fails with no cached map to fall back on, or the bytes don't decode — the
     * card then simply doesn't appear. Mirrors `SurfaceAnalysisService.frame(force:)`.
     */
    suspend fun map(force: Boolean = false): AuraSurfaceInfo? {
        if (!force) {
            cachedMap(maxAgeMillis = ttlMillis)?.let { return it }
        }

        val key = secretStore.apiKey()
        if (key.isNullOrEmpty()) {
            // No key: any stale map beats none (maxAge infinite), matching iOS's offline fallback.
            return cachedMap(maxAgeMillis = Long.MAX_VALUE)
        }

        return try {
            val bytes = AemetClient(key).surfaceAnalysis()
            val image = decode(bytes) ?: return cachedMap(maxAgeMillis = Long.MAX_VALUE)
            // The nominal 00/12 UTC slot at or before now: what the map is "valid for", and its cache name.
            val issue = currentSlot(Instant.now())
            val file = cacheFile(issue)
            // Only persist bytes that actually decoded (and rotated), so a corrupt payload never poisons the
            // cache. We store the ORIGINAL bytes: decode+rotate is cheap and keeps the disk copy as AEMET sent
            // it, so a future decoder change needs no re-fetch.
            runCatching { withContext(Dispatchers.IO) { file.writeBytes(bytes) } }
            AuraSurfaceInfo(image = image, issue = issue)
        } catch (_: Exception) {
            cachedMap(maxAgeMillis = Long.MAX_VALUE)
        }
    }

    /**
     * Delete surface maps left in `cacheDir` older than [olderThanMillis] (default 48 h). The live TTL is
     * 12 h, so anything this old is dead weight; today we keep only the latest, but naming by issue slot means
     * a couple can pile up across a reissue. Direct port of `SurfaceAnalysisService.pruneCache`.
     */
    fun pruneCache(olderThanMillis: Long = 48 * 60 * 60 * 1000L) {
        val cutoff = Instant.now().toEpochMilli() - olderThanMillis
        appContext.cacheDir.listFiles()
            ?.filter { it.name.startsWith(filePrefix) && it.lastModified() < cutoff }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun cacheFile(issue: Instant): File =
        File(appContext.cacheDir, "$filePrefix${slotFormatter.format(issue)}.img")

    /** The newest cached `surface-*.img` file, or null if none exist. */
    private fun newestCacheFile(): File? =
        appContext.cacheDir.listFiles()
            ?.filter { it.name.startsWith(filePrefix) }
            ?.maxByOrNull { it.lastModified() }

    /**
     * The newest cached map if it exists, is younger than [maxAgeMillis], and still decodes. Its issue time is
     * parsed back from the filename slot (so the card's "valid at" line reflects the map, not our download
     * time); if the name doesn't parse, we fall back to the file's mtime.
     */
    private fun cachedMap(maxAgeMillis: Long): AuraSurfaceInfo? {
        val file = newestCacheFile() ?: return null
        val modified = file.lastModified()
        val ageOk = maxAgeMillis == Long.MAX_VALUE || Instant.now().toEpochMilli() - modified < maxAgeMillis
        if (!ageOk) return null
        val image = decode(file.readBytes()) ?: return null
        return AuraSurfaceInfo(image = image, issue = slotFrom(file) ?: Instant.ofEpochMilli(modified))
    }

    /** Parse the UTC issue slot back out of a `surface-<yyyyMMddHH>.img` filename, or null if it doesn't fit. */
    private fun slotFrom(file: File): Instant? =
        runCatching {
            val slot = file.name.removePrefix(filePrefix).removeSuffix(".img")
            java.time.LocalDateTime.parse(slot, slotFormatter).toInstant(ZoneOffset.UTC)
        }.getOrNull()

    /** The nominal 00/12 UTC issue at or before [now] — AEMET publishes the analysis on those two slots. */
    private fun currentSlot(now: Instant): Instant {
        val utc = now.atZone(ZoneOffset.UTC)
        val slotHour = if (utc.hour >= 12) 12 else 0
        return utc.withHour(slotHour).withMinute(0).withSecond(0).withNano(0).toInstant()
    }

    /**
     * Decode surface-analysis bytes (a single-frame GIF) and rotate 90° clockwise into a wide landscape
     * [ImageBitmap], or null if they don't decode. `BitmapFactory` decodes only the first frame of an animated
     * GIF, which is exactly what we want here (the analysis is a still); a null decode guards a corrupt or
     * unexpected payload.
     */
    private fun decode(bytes: ByteArray): ImageBitmap? {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotated = runCatching {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(90f) }, true)
        }.getOrNull() ?: return null
        return rotated.asImageBitmap()
    }
}
