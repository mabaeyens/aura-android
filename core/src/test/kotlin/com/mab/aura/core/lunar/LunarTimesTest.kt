package com.mab.aura.core.lunar

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Parity port of `LunarTimesTests.swift`. Pinned against timeanddate.com for Madrid (40.4168°N,
 * 3.7038°W), 2026-08-23: a waxing gibbous 80.2% illuminated, next full 28 Aug, next new 11 Sep, moonrise
 * 18:34 local (Europe/Madrid, UTC+2 in August → 16:34 UTC).
 */
class LunarTimesTest {

    private val madridLat = 40.4168
    private val madridLon = -3.7038

    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC)

    @Test
    fun illuminationMatchesReference() {
        // 2026-08-23 14:17 local = 12:17 UTC: reference says 80.2% illuminated, waxing.
        val p = LunarPosition(utc(2026, 8, 23, 12, 17))
        assertTrue(abs(p.illumination - 0.802) < 0.03)
        assertTrue(p.waxing)
    }

    @Test
    fun fullMoonIsFullyLit() {
        // Next full moon: 28 Aug 2026 06:18 local = 04:18 UTC.
        val p = LunarPosition(utc(2026, 8, 28, 4, 18))
        assertTrue(p.illumination > 0.99)
    }

    @Test
    fun newMoonIsDark() {
        // Next new moon: 11 Sep 2026 05:27 local = 03:27 UTC.
        val p = LunarPosition(utc(2026, 9, 11, 3, 27))
        assertTrue(p.illumination < 0.02)
    }

    @Test
    fun declinationInRange() {
        // Sanity: the Moon's declination stays within its ~±28.6° envelope.
        val p = LunarPosition(utc(2026, 8, 23, 12, 17))
        assertTrue(abs(p.declination) < 28.6)
    }

    @Test
    fun moonriseMatchesReference() {
        // At 12:00 UTC the Moon is below the horizon in Madrid; the next rise is the day's moonrise,
        // reference 16:34 UTC. Allow a few minutes for the abbreviated theory.
        val t = LunarTimes(utc(2026, 8, 23, 12, 0), madridLat, madridLon)
        val rise = t.moonrise
        assertNotNull(rise)
        val expected = utc(2026, 8, 23, 16, 34)
        assertTrue(abs(Duration.between(expected, rise).seconds) < 8 * 60)
    }

    @Test
    fun moonsetAfterMoonrise() {
        // A waxing-gibbous Moon rising in the evening sets after midnight: set must follow the rise.
        val t = LunarTimes(utc(2026, 8, 23, 12, 0), madridLat, madridLon)
        val rise = requireNotNull(t.moonrise) { "expected a rise" }
        val set = requireNotNull(t.moonset) { "expected a set" }
        assertTrue(set.isAfter(rise))
        // And within a reasonable window (the Moon is up ~10–11 h near full).
        assertTrue(Duration.between(rise, set).seconds < 14 * 3_600)
    }

    @Test
    fun moonUpReturnsPastRise() {
        // At 22:00 UTC on 23 Aug the Moon is well up (rose 16:34); the reported rise is that past crossing,
        // and the set is still ahead.
        val now = utc(2026, 8, 23, 22, 0)
        val t = LunarTimes(now, madridLat, madridLon)
        val rise = requireNotNull(t.moonrise) { "expected a current appearance's rise" }
        val set = requireNotNull(t.moonset) { "expected a current appearance's set" }
        assertTrue(rise.isBefore(now))
        assertTrue(set.isAfter(now))
    }
}
