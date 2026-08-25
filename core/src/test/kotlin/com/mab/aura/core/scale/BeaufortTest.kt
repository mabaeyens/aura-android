package com.mab.aura.core.scale

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the wind-speed to Beaufort-force mapping and the band-text edges the wind sheet reads against. */
class BeaufortTest {

    @Test
    fun forceMapsSpeedToBand() {
        assertEquals(0, Beaufort.force(0))     // calm
        assertEquals(1, Beaufort.force(5))     // top of Ventolina
        assertEquals(2, Beaufort.force(6))     // bottom of Flojito
        assertEquals(4, Beaufort.force(28))    // top of Bonancible
        assertEquals(11, Beaufort.force(117))  // top of the last bounded band
        assertEquals(12, Beaufort.force(118))  // into the open-ended top force
        assertEquals(12, Beaufort.force(300))  // still the top force, however strong
    }

    @Test
    fun forceIsNegativeWithoutAReading() {
        assertEquals(-1, Beaufort.force(null))
    }

    @Test
    fun rangeTextCoversCalmMiddleAndOpenTop() {
        assertEquals("menos de 1 km/h", Beaufort.scale.first { it.force == 0 }.rangeText)
        assertEquals("20–28 km/h", Beaufort.scale.first { it.force == 4 }.rangeText)
        assertEquals("más de 118 km/h", Beaufort.scale.first { it.force == 12 }.rangeText)
    }

    @Test
    fun scaleHasAllThirteenForces() {
        assertEquals(13, Beaufort.scale.size)
        assertEquals((0..12).toList(), Beaufort.scale.map { it.force })
    }
}
