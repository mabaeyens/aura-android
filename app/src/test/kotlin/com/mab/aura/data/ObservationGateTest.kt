package com.mab.aura.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pins [observationDue], the hourly clock that keeps a refresh from re-downloading AEMET's national
 * observation feed before a new reading is due. Ported alongside `AEMETService.swift`'s observation gate;
 * the gate itself has no Swift unit test (it lives in the app target there), so it's pinned here.
 */
class ObservationGateTest {

    // A 10:00 reading is next genuinely due at 11:30 (60 min cadence + 30 min publish-lag margin).
    private val fint = Instant.parse("2026-08-21T10:00:00Z")

    @Test
    fun force_alwaysFetches() {
        // Even seconds after the last reading, a forced refresh ignores the clock.
        assertTrue(observationDue(anchor = fint, now = fint.plusSeconds(60), force = true))
    }

    @Test
    fun missingAnchor_fetches() {
        // Nothing fetched yet (first ever refresh): fetch and record the anchor.
        assertTrue(observationDue(anchor = null, now = fint, force = false))
    }

    @Test
    fun withinTtl_skips() {
        // A refresh at 10:35, for a 10:00 reading next due at 11:30, makes zero calls to /todas.
        assertFalse(observationDue(anchor = fint, now = Instant.parse("2026-08-21T10:35:00Z"), force = false))
    }

    @Test
    fun pastTtl_fetches() {
        // Just past 11:30 the next reading is due, so the feed is fetched again.
        assertTrue(observationDue(anchor = fint, now = Instant.parse("2026-08-21T11:31:00Z"), force = false))
    }

    @Test
    fun futureAnchor_isClampedAndStillBecomesDue() {
        val now = Instant.parse("2026-08-21T10:00:00Z")
        val badFuture = now.plusSeconds(3600) // 11:00, a corrupt future-dated fint
        // Clamped to now, so a bad future timestamp doesn't push the threshold out to `fint + 90` from a
        // still-earlier "now"; evaluated at 10:00 it simply skips, as any fresh anchor would.
        assertFalse(observationDue(anchor = badFuture, now = now, force = false))
        // And it never gets stuck: once the clock is past the anchor's own +90-min window it fetches again.
        assertTrue(observationDue(anchor = badFuture, now = Instant.parse("2026-08-21T13:00:00Z"), force = false))
    }
}
