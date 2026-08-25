package com.mab.aura.ui.sky

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

/**
 * Pins the app-side sun/moon position maths, [AuraSunPath.from]. This is the first `:app` unit test: the
 * position logic returns a Compose [androidx.compose.ui.geometry.Offset], so it can't live in `:core`
 * without pulling Compose in, but it is otherwise pure and worth pinning here. Runs on the local JVM
 * (`./gradlew :app:testDebugUnitTest`).
 *
 * The reference day is 2026-06-21 with a 06:00 → 20:00 UTC sun (a tidy 14 h day, 10 h night), so the
 * fractions and altitudes come out to round numbers. `AuraSunPath.from` folds the sun times onto the
 * render day with [com.mab.aura.core.sky.AuraSunPath.onSameDay], which reads `ZoneId.systemDefault()`, so
 * the fixture pins the JVM default zone to UTC — otherwise these UTC instants would land on different
 * local hours on a machine in another timezone and the fractions would drift.
 */
class AuraSunPathTest {

    private lateinit var savedZone: TimeZone

    @Before
    fun pinZoneToUtc() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(savedZone)
    }

    private val sunrise: Instant = Instant.parse("2026-06-21T06:00:00Z")
    private val sunset: Instant = Instant.parse("2026-06-21T20:00:00Z")

    /** The apex: solar noon, halfway between sunrise and sunset. */
    private val solarNoon: Instant = Instant.parse("2026-06-21T13:00:00Z")

    @Test
    fun missingSunTimesGiveANeutralNoon() {
        // No sun data at all: a neutral high-noon sky, not night.
        val path = AuraSunPath.from(solarNoon, sunrise = null, sunset = null)
        assertFalse(path.isNight)
        assertEquals(1.0, path.altitude, 1e-9)
        assertEquals(0.5f, path.point.x, 1e-6f)
        assertEquals(0.16f, path.point.y, 1e-6f)
    }

    @Test
    fun daytimeMidpointIsHighNoon() {
        val path = AuraSunPath.from(solarNoon, sunrise, sunset)
        assertFalse(path.isNight)
        // sin(0.5·π) = 1: the sun is at its peak, x halfway across.
        assertEquals(1.0, path.altitude, 1e-9)
        assertEquals(0.5f, path.point.x, 1e-6f)
    }

    @Test
    fun sunriseIsTheEasternHorizon() {
        val path = AuraSunPath.from(sunrise, sunrise, sunset)
        assertFalse(path.isNight)
        // f = 0 at sunrise: altitude 0, riding the leading (east) edge.
        assertEquals(0.0, path.altitude, 1e-9)
        assertEquals(0.0f, path.point.x, 1e-6f)
    }

    @Test
    fun sunsetStillCountsAsDay() {
        // The window is inclusive of sunset (`!now.isAfter(ss)`), so the sun sits on the western horizon,
        // not yet flipped to night.
        val path = AuraSunPath.from(sunset, sunrise, sunset)
        assertFalse(path.isNight)
        assertEquals(0.0, path.altitude, 1e-9)
        assertEquals(1.0f, path.point.x, 1e-6f)
    }

    @Test
    fun afterSunsetIsNight() {
        val justAfter = sunset.plusSeconds(60)
        val path = AuraSunPath.from(justAfter, sunrise, sunset)
        assertTrue(path.isNight)
        // Just into the night: still near the eastern start of the moon's arc, low altitude.
        assertTrue(path.point.x < 0.05f)
        assertTrue(path.altitude < 0.05)
    }

    @Test
    fun deepNightIsHalfwayAcrossTheMoonArc() {
        // Night runs 20:00 → 06:00 (10 h); its midpoint is 01:00 the next day.
        val midnightish = Instant.parse("2026-06-22T01:00:00Z")
        val path = AuraSunPath.from(midnightish, sunrise, sunset)
        assertTrue(path.isNight)
        assertEquals(0.5f, path.point.x, 1e-6f)
        assertEquals(1.0, path.altitude, 1e-9)  // moon at its arc peak
    }

    @Test
    fun invertedSunTimesFallBackToNoon() {
        // A sunset not after sunrise is nonsensical (polar edge / bad data): fall back to the neutral noon.
        val path = AuraSunPath.from(solarNoon, sunrise = sunset, sunset = sunrise)
        assertFalse(path.isNight)
        assertEquals(1.0, path.altitude, 1e-9)
        assertEquals(0.5f, path.point.x, 1e-6f)
    }
}
