package com.mab.aura.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpainCitiesTest {

    @Test
    fun seedMatchesTheCuratedList() {
        // The 50 provincial capitals + Ceuta + Melilla, plus two extra majors (Gijón, Vigo) that are not
        // capitals — 54 in all, matching SpainCities.swift one for one.
        assertEquals(54, SpainCities.seed.size)
    }

    @Test
    fun everySeedIneIsFiveDigits() {
        for (city in SpainCities.seed) {
            assertTrue(
                "INE ${city.ine} (${city.nombre}) should be 5 digits",
                city.ine.length == 5 && city.ine.all { it.isDigit() },
            )
        }
    }

    @Test
    fun seedInesAreUnique() {
        val ines = SpainCities.seed.map { it.ine }
        assertEquals(ines.size, ines.toSet().size)
    }

    @Test
    fun nearestResolvesTheCityAtItsOwnCoordinate() {
        // Every seed city must be its own nearest match — the cheapest sanity check that the distance
        // ranking is sound across the whole list.
        for (city in SpainCities.seed) {
            assertEquals(
                "nearest to ${city.nombre} should be itself",
                city.ine,
                SpainCities.nearest(city.latitude, city.longitude).ine,
            )
        }
    }

    @Test
    fun nearestResolvesAPointNearMadridToMadrid() {
        // Puerta del Sol, a little off the stored centroid — still unambiguously Madrid.
        val madrid = SpainCities.nearest(40.4168, -3.7038)
        assertEquals("28079", madrid.ine)
        assertEquals("Madrid", madrid.nombre)
    }

    @Test
    fun nearestResolvesAPointNearBarcelonaToBarcelona() {
        // Sagrada Família, comfortably closer to Barcelona than to Girona or Tarragona.
        val barcelona = SpainCities.nearest(41.4036, 2.1744)
        assertEquals("08019", barcelona.ine)
        assertEquals("Barcelona", barcelona.nombre)
    }
}
