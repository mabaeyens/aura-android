package com.mab.aura.store

import android.content.Context
import com.mab.aura.core.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * The on-device weather cache: the app fetches from AEMET, normalises to [WeatherSnapshot]s, and stores them
 * here; the UI (and later the Glance widget, in the same process) renders from whatever this holds, falling
 * back to the last known values when offline. Android port of the file half of `SharedCache.swift`.
 *
 * No App Group is needed on Android (the widget shares the app process), so this is a plain app-private JSON
 * file in `filesDir`, not a shared container. All access is suspending and hops to [Dispatchers.IO] — file
 * reads and writes must never run on the main thread. The write is atomic (write to a temp file, then
 * rename) so a crash mid-write can't leave a half-written cache. The upsert/prune list logic is pulled out as
 * pure functions below so it can be unit-tested without a device.
 */
class SnapshotCache(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    /** All cached snapshots, newest write wins per location. Empty if nothing has been cached yet. */
    suspend fun read(): List<WeatherSnapshot> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString<List<WeatherSnapshot>>(file.readText()) }
            .getOrDefault(emptyList())
    }

    /** The cached snapshot for one municipality, if present. */
    suspend fun snapshot(ine: String): WeatherSnapshot? = read().firstOrNull { it.ine == ine }

    /** Replace all cached snapshots. */
    suspend fun write(snapshots: List<WeatherSnapshot>): Unit = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(snapshots))
        // renameTo is atomic on the same filesystem; fall back to a plain copy if the platform refuses it.
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /** Insert or update the snapshot for its location, leaving the others untouched. */
    suspend fun upsert(snapshot: WeatherSnapshot) {
        write(upsertSnapshot(read(), snapshot))
    }

    /**
     * Trim the cache so it can't grow without bound as favourites come and go. Only rewrites the file when it
     * actually removed something, so it is cheap to call on every launch/refresh. See [pruneSnapshots].
     */
    suspend fun prune(
        keepINEs: Set<String>? = null,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
        maxCount: Int = DEFAULT_MAX_COUNT,
        now: Instant = Instant.now(),
    ) {
        val all = read()
        val pruned = pruneSnapshots(all, keepINEs, maxAgeMillis, maxCount, now)
        if (pruned.size != all.size) write(pruned)
    }

    private companion object {
        const val FILE_NAME = "snapshots.json"
        // 30 days, and cap at 24 locations — a final safety net matching the Swift defaults.
        const val DEFAULT_MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val DEFAULT_MAX_COUNT = 24

        val json = Json { ignoreUnknownKeys = true }
    }
}

// --- Pure list logic (no Context / no IO), extracted so it is unit-testable on the JVM ---

/** Replace any existing snapshot for the same location, then append the new one. */
internal fun upsertSnapshot(all: List<WeatherSnapshot>, snapshot: WeatherSnapshot): List<WeatherSnapshot> =
    all.filter { it.ine != snapshot.ine } + snapshot

/**
 * Drop any snapshot older than [maxAgeMillis]; when [keepINEs] is given, also drop any location no longer in
 * that set (a favourite the user removed); then cap the store to the [maxCount] most-recently-updated
 * entries. Direct port of `SharedCache.prune`.
 */
internal fun pruneSnapshots(
    all: List<WeatherSnapshot>,
    keepINEs: Set<String>?,
    maxAgeMillis: Long,
    maxCount: Int,
    now: Instant,
): List<WeatherSnapshot> {
    val kept = all.filter { snapshot ->
        val ageMillis = now.toEpochMilli() - snapshot.updated.toEpochMilli()
        when {
            ageMillis >= maxAgeMillis -> false
            keepINEs != null && !keepINEs.contains(snapshot.ine) -> false
            else -> true
        }
    }
    return if (kept.size > maxCount) {
        kept.sortedByDescending { it.updated }.take(maxCount)
    } else {
        kept
    }
}
