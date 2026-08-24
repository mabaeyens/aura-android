package com.mab.aura.core.air

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.Instant
import org.junit.Test

/**
 * Pins the pure `AirComponent`/`AirQuality` logic ported from `AirQuality.swift`. The Swift
 * `AirComponentsTests` `valueText`/`label` cases port directly; its parse/requestBody cases and the
 * whole of `AirQualityTests` exercise the MITECO client (CSV, backend POST, nearest-station), which is
 * Layer C and not ported yet. The band/category/fraction edges have no Swift test to port from, so they
 * pin the official ICA breakpoints directly.
 */
class AirQualityTest {

    // --- Ported from AirComponentsTests.testValueTextFormatting / testLabels ---

    @Test
    fun valueText_spanishCommaAndWholeNumbers() {
        assertEquals("12,5", AirComponent(pollutant = "PM10", value = 12.5).valueText)
        assertEquals("27", AirComponent(pollutant = "NO2", value = 27.0).valueText)
        assertEquals("60", AirComponent(pollutant = "O3", value = 60.0).valueText)
    }

    @Test
    fun labels_haveProperSubscripts() {
        assertEquals("NO₂", AirComponent(pollutant = "NO2", value = 1.0).label)
        assertEquals("O₃", AirComponent(pollutant = "O3", value = 1.0).label)
        assertEquals("PM2,5", AirComponent(pollutant = "PM2.5", value = 1.0).label)
        assertEquals("PM10", AirComponent(pollutant = "PM10", value = 1.0).label)
        assertEquals("SO₂", AirComponent(pollutant = "SO2", value = 1.0).label)
    }

    // --- Band / category / fraction edges (NO₂ bands: 40, 90, 120, 230, 340) ---

    @Test
    fun icaCategory_atEveryNo2BandEdge() {
        fun cat(v: Double) = AirComponent(pollutant = "NO2", value = v).icaCategory
        assertEquals(1, cat(0.0))
        assertEquals(1, cat(40.0))   // value <= upper is inclusive
        assertEquals(2, cat(40.1))
        assertEquals(2, cat(90.0))
        assertEquals(3, cat(120.0))
        assertEquals(4, cat(230.0))
        assertEquals(5, cat(340.0))
        assertEquals(6, cat(340.1))  // open-ended top band
    }

    @Test
    fun icaCategory_unknownTokenIsZero() {
        assertEquals(0, AirComponent(pollutant = "CO", value = 5.0).icaCategory)
    }

    @Test
    fun icaFraction_isContinuousAcrossTheOpenTopBand() {
        // At the top of category 5 the climb is full: (4 + 1) / 6 = 5/6.
        val cat5Top = AirComponent(pollutant = "NO2", value = 340.0).icaFraction
        assertEquals(5.0 / 6.0, cat5Top, 1e-9)
        // Just above 340 the value crosses into the open-ended category 6 with a near-zero climb, so the
        // fraction stays continuous rather than jumping.
        val cat6Start = AirComponent(pollutant = "NO2", value = 340.0001).icaFraction
        assertEquals(5.0 / 6.0, cat6Start, 1e-3)
    }

    @Test
    fun icaFraction_unknownTokenIsZero() {
        assertEquals(0.0, AirComponent(pollutant = "CO", value = 5.0).icaFraction, 0.0)
    }

    // --- AirQuality ---

    @Test
    fun create_sortsComponentsIntoCanonicalOrder() {
        val aq = AirQuality.create(
            category = 1, partial = false, pollutant = "O3",
            station = "Valderejo", distanceKm = 5.0, measured = Instant.EPOCH,
            components = listOf(
                AirComponent(pollutant = "SO2", value = 4.0),
                AirComponent(pollutant = "O3", value = 60.0),
                AirComponent(pollutant = "NO2", value = 3.0),
            ),
        )
        assertEquals(listOf("NO2", "O3", "SO2"), aq.components.map { it.pollutant })
    }

    @Test
    fun adding_keepsHeadlineAndSortsComponents() {
        val base = AirQuality.create(
            category = 2, partial = true, pollutant = "PM10",
            station = "Retiro", distanceKm = 0.4, measured = Instant.EPOCH,
        )
        val withComps = base.adding(
            listOf(
                AirComponent(pollutant = "PM10", value = 30.0),
                AirComponent(pollutant = "NO2", value = 12.0),
            ),
        )
        assertEquals(2, withComps.category)
        assertEquals(true, withComps.partial)
        assertEquals("Retiro", withComps.station)
        assertEquals(listOf("NO2", "PM10"), withComps.components.map { it.pollutant })
    }

    @Test
    fun categoryName_coversTheSixLevelsAndNoData() {
        assertEquals("Buena", AirQuality.categoryName(1))
        assertEquals("Razonablemente buena", AirQuality.categoryName(2))
        assertEquals("Regular", AirQuality.categoryName(3))
        assertEquals("Desfavorable", AirQuality.categoryName(4))
        assertEquals("Muy desfavorable", AirQuality.categoryName(5))
        assertEquals("Extremadamente desfavorable", AirQuality.categoryName(6))
        assertEquals("Sin datos", AirQuality.categoryName(0))
    }

    @Test
    fun pollutantLabel_subscriptsAndNullCases() {
        fun aq(p: String?) = AirQuality.create(1, false, p, "S", 1.0, Instant.EPOCH)
        assertEquals("O₃", aq("O3").pollutantLabel)
        assertEquals("PM2,5", aq("PM2.5").pollutantLabel)
        assertNull(aq(null).pollutantLabel)
        assertNull(aq("").pollutantLabel)
    }
}
