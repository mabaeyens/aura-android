package com.mab.aura.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Pins the shared clock formatting both ways round the 24 h / 12 h toggle, ported from `AuraTime.swift`. */
class AuraTimeTest {

    private val utc = ZoneId.of("UTC")
    // 2026-06-21T18:34Z and 06:05Z, formatted in UTC so the wall-clock is exactly the instant's UTC time.
    private val evening = Instant.parse("2026-06-21T18:34:00Z")
    private val morning = Instant.parse("2026-06-21T06:05:00Z")
    private val noon = Instant.parse("2026-06-21T12:00:00Z")
    private val midnight = Instant.parse("2026-06-21T00:00:00Z")

    @Test
    fun hhmm24hIsZeroPaddedTwentyFourHour() {
        assertEquals("18:34", AuraTime.hhmm(evening, use24h = true, zone = utc))
        assertEquals("06:05", AuraTime.hhmm(morning, use24h = true, zone = utc))
        assertEquals("00:00", AuraTime.hhmm(midnight, use24h = true, zone = utc))
    }

    @Test
    fun hhmm12hUsesAmPmWithNoLeadingHourZero() {
        assertEquals("6:34 PM", AuraTime.hhmm(evening, use24h = false, zone = utc))
        assertEquals("6:05 AM", AuraTime.hhmm(morning, use24h = false, zone = utc))
        assertEquals("12:00 PM", AuraTime.hhmm(noon, use24h = false, zone = utc))
        assertEquals("12:00 AM", AuraTime.hhmm(midnight, use24h = false, zone = utc))
    }

    @Test
    fun hourLabel24hIsBareHourWithSuffix() {
        assertEquals("0h", AuraTime.hourLabel(0, use24h = true))
        assertEquals("9h", AuraTime.hourLabel(9, use24h = true))
        assertEquals("23h", AuraTime.hourLabel(23, use24h = true))
    }

    @Test
    fun hourLabel12hWrapsMiddayAndMidnightToTwelve() {
        assertEquals("12 AM", AuraTime.hourLabel(0, use24h = false))
        assertEquals("6 AM", AuraTime.hourLabel(6, use24h = false))
        assertEquals("12 PM", AuraTime.hourLabel(12, use24h = false))
        assertEquals("1 PM", AuraTime.hourLabel(13, use24h = false))
        assertEquals("11 PM", AuraTime.hourLabel(23, use24h = false))
    }

    @Test
    fun defaultPreferenceIsTwentyFourHour() {
        // The shared flag defaults to Spain's 24 h clock before :app reads the stored value.
        assertEquals(true, AuraTime.use24h)
        assertEquals("18:34", AuraTime.hhmm(evening, zone = utc))
    }
}
