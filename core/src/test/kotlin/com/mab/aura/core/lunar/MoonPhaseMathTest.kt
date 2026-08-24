package com.mab.aura.core.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.min

/** Parity port of `MoonPhaseTests.swift`. */
class MoonPhaseMathTest {

    private fun utc(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0): Instant =
        java.time.LocalDateTime.of(y, mo, d, h, mi).toInstant(java.time.ZoneOffset.UTC)

    @Test
    fun referenceNewMoonIsDark() {
        val p = MoonPhaseMath.fraction(MoonPhaseMath.referenceNewMoon)
        // At the reference instant we are exactly at new: fraction ~0, illumination ~0.
        assertTrue("fraction should sit at the new-moon wrap point", min(p, 1 - p) < 0.01)
        assertTrue(MoonPhaseMath.illumination(p) < 0.01)
    }

    @Test
    fun fullMoonRoughlyHalfCycleLater() {
        val full = MoonPhaseMath.referenceNewMoon
            .plusMillis((MoonPhaseMath.SYNODIC_MONTH / 2 * 86_400 * 1000).toLong())
        val p = MoonPhaseMath.fraction(full)
        assertEquals(0.5, p, 0.01)
        assertTrue(MoonPhaseMath.illumination(p) > 0.99)
    }

    @Test
    fun aug2026IsWaxingGibbousAround61Percent() {
        val p = MoonPhaseMath.fraction(utc(2026, 8, 21))
        assertTrue("21 Aug 2026 should be waxing", MoonPhaseMath.waxing(p))
        assertEquals(0.61, MoonPhaseMath.illumination(p), 0.12)
    }

    @Test
    fun illuminationSymmetryAroundFull() {
        var p = 0.05
        while (p <= 0.45 + 1e-9) {
            assertEquals(MoonPhaseMath.illumination(p), MoonPhaseMath.illumination(1 - p), 1e-9)
            p += 0.05
        }
    }

    @Test
    fun waxingSplitsCycleAtFull() {
        assertTrue(MoonPhaseMath.waxing(0.0))
        assertTrue(MoonPhaseMath.waxing(0.49))
        assertFalse(MoonPhaseMath.waxing(0.5))
        assertFalse(MoonPhaseMath.waxing(0.99))
    }

    @Test
    fun phaseNamesAtPrincipalFractions() {
        assertEquals("Luna nueva", MoonPhaseMath.phaseName(0.0))
        assertEquals("Cuarto creciente", MoonPhaseMath.phaseName(0.25))
        assertEquals("Luna llena", MoonPhaseMath.phaseName(0.5))
        assertEquals("Cuarto menguante", MoonPhaseMath.phaseName(0.75))
        // The wrap: just shy of a full cycle rounds back to new.
        assertEquals("Luna nueva", MoonPhaseMath.phaseName(0.97))
    }

    @Test
    fun nextNewMoonIsInTheFutureAndNew() {
        val from = utc(2026, 8, 21)
        val next = MoonPhaseMath.nextNewMoon(from)
        assertTrue(next.isAfter(from))
        assertTrue(
            Duration.between(from, next).toMillis() <=
                (MoonPhaseMath.SYNODIC_MONTH * 86_400 * 1000).toLong() + 1000,
        )
        assertTrue(MoonPhaseMath.illumination(MoonPhaseMath.fraction(next)) < 0.02)
    }

    @Test
    fun nextFullMoonIsInTheFutureAndFull() {
        val from = utc(2026, 8, 21)
        val next = MoonPhaseMath.nextFullMoon(from)
        assertTrue(next.isAfter(from))
        assertTrue(
            Duration.between(from, next).toMillis() <=
                (MoonPhaseMath.SYNODIC_MONTH * 86_400 * 1000).toLong() + 1000,
        )
        assertTrue(MoonPhaseMath.illumination(MoonPhaseMath.fraction(next)) > 0.98)
    }

    @Test
    fun nextFullMoonFromJustBeforeFullIsImminent() {
        val nearFull = MoonPhaseMath.referenceNewMoon
            .plusMillis(((MoonPhaseMath.SYNODIC_MONTH / 2 - 1) * 86_400 * 1000).toLong())
        val next = MoonPhaseMath.nextFullMoon(nearFull)
        assertTrue(Duration.between(nearFull, next).toMillis() < 2L * 86_400 * 1000)
    }
}
