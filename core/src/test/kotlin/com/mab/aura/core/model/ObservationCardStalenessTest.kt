package com.mab.aura.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Pins the observation-card staleness gate (unified-freshness concept 3): the reading's own age decides
 * whether its card shows, not just whether a station resolved. Two pure display-time helpers on
 * [WeatherSnapshot]:
 *
 *  - [WeatherSnapshot.observationIsFresh]: the reading is within [StationObservation.OBSERVATION_MAX_AGE]
 *    (3 h) of now and not in the future. Boundary-locked with the iOS `observationIsFresh` vectors: exactly
 *    3 h is fresh (`<=`), 3 h + 1 min is stale, a future reading is never fresh.
 *  - [WeatherSnapshot.observationDisplayTime]: the measured-at "HH:MM" time, returned only when the reading
 *    is not from the current clock hour (a current-hour reading is "now" and needs no stamp); an earlier hour
 *    — including the same hour on a different day — returns a time. The localized "a las %s" / "at %s" prefix
 *    that wraps it is chrome and lives in :app, so it is not part of this :core value.
 *
 * UTC is injected so a reading's hour maps straight to the instant's UTC hour and the stamp is easy to read.
 * The bounded carry-forward (make() drops a reading once it ages past the gate) is pinned in
 * [WeatherSnapshotFactoryTest], where the AEMET-mapping fixtures live.
 */
class ObservationCardStalenessTest {

    private val utc = ZoneId.of("UTC")
    private fun at(iso: String) = Instant.parse(iso)

    /** A minimal snapshot carrying only the observed measurement time under test. */
    private fun withObservedAt(observedAt: Instant?, observedTemp: Int? = 20) = WeatherSnapshot(
        ine = "28079", localidad = "Madrid", provincia = "Madrid",
        observedTemp = observedTemp, observedAt = observedAt,
        updated = at("2024-01-15T00:00:00Z"),
    )

    // --- observationIsFresh: within 3 h and not in the future ---

    @Test
    fun isFresh_exactlyThreeHoursOldIsFresh() {
        val now = at("2024-01-15T14:00:00Z")
        // Exactly OBSERVATION_MAX_AGE (3 h) old — the boundary is inclusive (<=), matching iOS.
        assertTrue(withObservedAt(at("2024-01-15T11:00:00Z")).observationIsFresh(now))
    }

    @Test
    fun isFresh_oneMinutePastThreeHoursIsStale() {
        val now = at("2024-01-15T14:00:00Z")
        assertFalse(withObservedAt(at("2024-01-15T10:59:00Z")).observationIsFresh(now))
    }

    @Test
    fun isFresh_recentReadingIsFresh() {
        val now = at("2024-01-15T14:00:00Z")
        assertTrue(withObservedAt(at("2024-01-15T13:30:00Z")).observationIsFresh(now))
    }

    @Test
    fun isFresh_futureReadingIsNeverFresh() {
        val now = at("2024-01-15T14:00:00Z")
        // Clock skew: a measurement stamped after `now` is rejected outright (mirrors iOS's `fint <= now`).
        assertFalse(withObservedAt(at("2024-01-15T14:01:00Z")).observationIsFresh(now))
    }

    @Test
    fun isFresh_missingTimestampIsStale() {
        assertFalse(withObservedAt(null).observationIsFresh(at("2024-01-15T14:00:00Z")))
    }

    // --- observationDisplayTime: stamp only off the current clock hour ---

    @Test
    fun displayTime_currentClockHourHasNoStamp() {
        val now = at("2024-01-15T14:23:00Z")
        assertNull(withObservedAt(at("2024-01-15T14:00:00Z")).observationDisplayTime(now, utc))
    }

    @Test
    fun displayTime_earlierHourReturnsTheTime() {
        // Only the time; the localized "a las %s" / "at %s" prefix is added by the card in :app.
        val now = at("2024-01-15T14:23:00Z")
        assertEquals("13:00", withObservedAt(at("2024-01-15T13:00:00Z")).observationDisplayTime(now, utc))
    }

    @Test
    fun displayTime_sameHourDifferentDayReturnsTheTime() {
        // Yesterday at the same wall-clock hour is a different absolute hour, so it still returns a time.
        val now = at("2024-01-15T14:23:00Z")
        assertEquals("14:00", withObservedAt(at("2024-01-14T14:00:00Z")).observationDisplayTime(now, utc))
    }

    @Test
    fun displayTime_missingTimestampHasNoStamp() {
        assertNull(withObservedAt(null).observationDisplayTime(at("2024-01-15T14:23:00Z"), utc))
    }
}
