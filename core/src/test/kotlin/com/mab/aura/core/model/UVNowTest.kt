package com.mab.aura.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/**
 * Pins [UVNow.from], the scalar UV readouts the "Índice UV" card captions its hourly strip with. No Swift
 * `UVNowTests` exists (the logic lived inline in `UVHourStrip`), so these pin the peak, the live "Ahora"
 * value, and the protection-window arithmetic directly. Hours are read in UTC so the fixtures line up with
 * the epoch hours below.
 */
class UVNowTest {

    private val utc: ZoneId = ZoneOffset.UTC
    // Epoch aligned to a UTC midnight, so slot `h` sits exactly at hour-of-day `h`.
    private val midnight = Instant.parse("2026-06-21T00:00:00Z")
    private fun slot(hour: Long, uv: Double) = UVHourSlot(midnight.plusSeconds(hour * 3600), uv, uv)

    // A plausible summer day: UV climbs from 07:00, peaks 8 at 13:00, fades by 19:00.
    private val today = listOf(
        slot(7, 1.4), slot(8, 2.6), slot(9, 4.1), slot(10, 5.8), slot(11, 7.0),
        slot(12, 7.8), slot(13, 8.2), slot(14, 7.6), slot(15, 6.1), slot(16, 4.3),
        slot(17, 2.7), slot(18, 1.2),
    )

    @Test
    fun peak_isTodaysHighestSlot() {
        val uv = UVNow.from(today, now = midnight.plusSeconds(13 * 3600), zone = utc)
        assertEquals(8, uv.peakIndex)   // 8.2 rounds to 8
        assertEquals(13, uv.peakHour)
    }

    @Test
    fun nowIndex_isTheLiveHourValue() {
        // Midway through hour 10 (uv 5.8 → index 6).
        val uv = UVNow.from(today, now = midnight.plusSeconds(10 * 3600 + 1800), zone = utc)
        assertEquals(6, uv.nowIndex)
    }

    @Test
    fun nowIndex_isNullOutsideTheDaytimeSpan() {
        // 03:00, before the first daytime slot → no covering slot.
        val uv = UVNow.from(today, now = midnight.plusSeconds(3 * 3600), zone = utc)
        assertNull(uv.nowIndex)
    }

    @Test
    fun protectionWindow_spansTheHoursAtOrAboveThreshold() {
        // Index uses rounding: 08:00 (2.6) and 17:00 (2.7) both round to 3, so they're the first and last
        // hours at the WHO threshold. End is last + 1 → 18.
        val uv = UVNow.from(today, now = midnight.plusSeconds(13 * 3600), zone = utc)
        assertEquals(8..18, uv.protection)
    }

    @Test
    fun protectionWindow_isNullWhenUvNeverReachesThreshold() {
        val lowDay = listOf(slot(10, 1.0), slot(12, 1.8), slot(14, 1.2))
        val uv = UVNow.from(lowDay, now = midnight.plusSeconds(12 * 3600), zone = utc)
        assertNull(uv.protection)
        assertEquals(2, uv.peakIndex) // 1.8 rounds to 2 — a peak still resolves
    }

    @Test
    fun emptySeries_isAllNull() {
        val uv = UVNow.from(emptyList(), now = midnight, zone = utc)
        assertNull(uv.nowIndex)
        assertNull(uv.peakIndex)
        assertNull(uv.peakHour)
        assertNull(uv.protection)
    }

    // --- cloudy: is the current sky holding the UV below its clear-sky value? ---

    private fun snapshot(sky: String?) = WeatherSnapshot(
        ine = "28079", localidad = "Madrid", provincia = "Madrid", currentSky = sky, updated = midnight,
    )

    @Test
    fun cloudy_isTrueForOvercastRainStormSnowFog() {
        // 16 = overcast, 25 = rain, 52 = storm, 71 = snow, 81 = fog (representative AEMET codes).
        for (code in listOf("16", "25", "52", "71", "81")) {
            assertEquals("code $code should read cloudy", true, UVNow.cloudy(snapshot(code)))
        }
    }

    @Test
    fun cloudy_isFalseForClearAndFewClouds() {
        for (code in listOf("11", "12", "13", null)) {
            assertEquals("code $code should not read cloudy", false, UVNow.cloudy(snapshot(code)))
        }
    }
}
