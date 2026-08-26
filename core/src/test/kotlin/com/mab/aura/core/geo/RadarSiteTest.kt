package com.mab.aura.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarSiteTest {

    @Test
    fun allFifteenRadarsPresentWithUniqueCodes() {
        assertEquals(15, RadarSite.all.size)
        assertEquals(15, RadarSite.all.map { it.code }.toSet().size)
    }

    @Test
    fun nearestPicksTheRadarOnTopOfEachCity() {
        // A location right on a radar city resolves to that radar (Barcelona is code "ba", the emulator's
        // default test location; Madrid, the Canaries, and Sevilla cover the mainland corners plus the islands).
        assertEquals("ba", RadarSite.nearest(41.39, 2.16).code)
        assertEquals("ma", RadarSite.nearest(40.42, -3.70).code)
        assertEquals("ca", RadarSite.nearest(28.10, -15.41).code)
        assertEquals("se", RadarSite.nearest(37.39, -5.99).code)
    }

    @Test
    fun nearestPrefersTheCloserOfTwoRadars() {
        // Girona (41.98, 2.82) sits between Barcelona (ba) and Zaragoza (za); Barcelona is far closer.
        assertEquals("ba", RadarSite.nearest(41.98, 2.82).code)
    }

    @Test
    fun nearestNeverReturnsNullEvenFarOffshore() {
        // Well out into the Atlantic, past every radar — still returns the closest, never crashes.
        val site = RadarSite.nearest(35.0, -30.0)
        assertTrue(RadarSite.all.contains(site))
    }
}
