package com.mab.aura.store

import com.mab.aura.core.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Pins the pure upsert/prune list logic behind [SnapshotCache] (ported from `SharedCache.prune`), the part
 * worth testing. The file IO itself needs a device, but this arithmetic doesn't — it runs on the JVM.
 */
class SnapshotCacheLogicTest {

    private val now = Instant.parse("2026-08-25T12:00:00Z")

    private fun snap(ine: String, ageDays: Long): WeatherSnapshot = WeatherSnapshot(
        ine = ine,
        localidad = "Loc$ine",
        provincia = "Prov",
        updated = now.minusSeconds(ageDays * 24 * 60 * 60),
    )

    private val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000

    @Test
    fun upsertReplacesTheSameLocationAndKeepsTheRest() {
        val madridOld = snap("28079", ageDays = 2)
        val barcelona = snap("08019", ageDays = 1)
        val madridNew = snap("28079", ageDays = 0)

        val result = upsertSnapshot(listOf(madridOld, barcelona), madridNew)

        assertEquals(2, result.size)
        // Barcelona untouched; Madrid replaced by the fresh one (and moved to the end, as the Swift appends).
        assertEquals(listOf("08019", "28079"), result.map { it.ine })
        assertEquals(madridNew.updated, result.first { it.ine == "28079" }.updated)
    }

    @Test
    fun upsertAppendsAnUnseenLocation() {
        val result = upsertSnapshot(listOf(snap("28079", 0)), snap("41091", 0))
        assertEquals(listOf("28079", "41091"), result.map { it.ine })
    }

    @Test
    fun pruneDropsSnapshotsOlderThanMaxAge() {
        val fresh = snap("28079", ageDays = 5)
        val stale = snap("08019", ageDays = 31) // past the 30-day window
        val result = pruneSnapshots(listOf(fresh, stale), keepINEs = null, thirtyDaysMillis, maxCount = 24, now)
        assertEquals(listOf("28079"), result.map { it.ine })
    }

    @Test
    fun pruneDropsLocationsNoLongerFavourited() {
        val kept = snap("28079", ageDays = 1)
        val removed = snap("08019", ageDays = 1)
        val result = pruneSnapshots(
            listOf(kept, removed),
            keepINEs = setOf("28079"),
            thirtyDaysMillis,
            maxCount = 24,
            now,
        )
        assertEquals(listOf("28079"), result.map { it.ine })
    }

    @Test
    fun pruneCapsToTheMostRecentlyUpdated() {
        // Five locations, cap at 3: the three freshest survive, newest first.
        val all = listOf(
            snap("A", ageDays = 4),
            snap("B", ageDays = 0),
            snap("C", ageDays = 2),
            snap("D", ageDays = 1),
            snap("E", ageDays = 3),
        )
        val result = pruneSnapshots(all, keepINEs = null, thirtyDaysMillis, maxCount = 3, now)
        assertEquals(listOf("B", "D", "C"), result.map { it.ine })
    }

    @Test
    fun pruneLeavesAHealthyCacheUntouched() {
        val all = listOf(snap("28079", 1), snap("08019", 2), snap("41091", 3))
        val result = pruneSnapshots(all, keepINEs = null, thirtyDaysMillis, maxCount = 24, now)
        assertEquals(all.map { it.ine }, result.map { it.ine })
    }
}
