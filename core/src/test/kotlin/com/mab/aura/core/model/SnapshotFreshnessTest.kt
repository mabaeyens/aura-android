package com.mab.aura.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ported from the iOS `SnapshotFreshnessTests`. The widget renders whatever the app last wrote to the shared
 * cache and never fetches on its own, so a day-old snapshot must at least *admit* it is old rather than
 * showing a silent stale value. The badge is driven only by `updated` vs the render `now`: fresh within the
 * hour (no badge), a soft "actualizado HH:mm" once it is older, and a hard "Desactualizado" past the ~24 h
 * strip horizon where display-time resolution can no longer keep the values correct.
 */
class SnapshotFreshnessTest {

    private val tz = ZoneId.of("Europe/Madrid")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Instant =
        LocalDateTime.of(y, mo, d, h, mi).atZone(tz).toInstant()

    /** A snapshot carrying only an `updated` stamp — the only field the badge reads. */
    private fun snapshot(updated: Instant) =
        WeatherSnapshot(ine = "28079", localidad = "Madrid", provincia = "Madrid", updated = updated)

    /** Under an hour old: current within the app's own stale gate, so no badge at all. */
    @Test
    fun freshWithinHourHasNoBadge() {
        val s = snapshot(at(2026, 8, 28, 14, 30))
        val now = at(2026, 8, 28, 15, 0) // 30 min later
        assertEquals(SnapshotFreshness.FRESH, s.freshness(now))
        assertNull(s.stalenessLabel(now, tz))
    }

    /** Older than an hour but within the 24 h horizon: soft, informational "actualizado HH:mm" in the
     *  location's own time — the values are still correct, the user is just told when they were fetched. */
    @Test
    fun recentShowsUpdatedTime() {
        val s = snapshot(at(2026, 8, 28, 14, 30))
        val now = at(2026, 8, 28, 17, 30) // 3 h later
        assertEquals(SnapshotFreshness.RECENT, s.freshness(now))
        assertEquals("actualizado 14:30", s.stalenessLabel(now, tz))
    }

    /** Exactly one hour old is the first moment it is no longer fresh. */
    @Test
    fun boundaryAtOneHourIsRecent() {
        val s = snapshot(at(2026, 8, 28, 14, 0))
        val now = at(2026, 8, 28, 15, 0) // exactly 1 h later
        assertEquals(SnapshotFreshness.RECENT, s.freshness(now))
    }

    /** Past the ~24 h strip horizon the hero can no longer re-anchor to today, so the badge escalates from
     *  informational to an honest "Desactualizado". */
    @Test
    fun dayOldIsStale() {
        val s = snapshot(at(2026, 8, 27, 13, 0))
        val now = at(2026, 8, 28, 15, 0) // 26 h later
        assertEquals(SnapshotFreshness.STALE, s.freshness(now))
        assertEquals("Desactualizado", s.stalenessLabel(now, tz))
    }

    /** A stamp in the future (device clock skew) must never read as stale — treat it as fresh, no badge. */
    @Test
    fun futureUpdatedIsFresh() {
        val s = snapshot(at(2026, 8, 28, 16, 0))
        val now = at(2026, 8, 28, 15, 0) // updated an hour "ahead" of now
        assertEquals(SnapshotFreshness.FRESH, s.freshness(now))
        assertNull(s.stalenessLabel(now, tz))
    }
}
