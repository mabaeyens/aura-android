package com.mab.aura.core.icon

import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks the AEMET-code → [WeatherGlyph] mapping (the port of `WeatherIcon.swift`'s switch). */
class WeatherIconTest {

    @Test
    fun clearHasDayAndNightVariants() {
        assertEquals(WeatherGlyph.CLEAR_DAY, WeatherIcon.glyph("11"))
        assertEquals(WeatherGlyph.CLEAR_NIGHT, WeatherIcon.glyph("11n"))
    }

    @Test
    fun fewCloudsGroupsTwelveThirteenSeventeen() {
        for (code in listOf("12", "13", "17")) {
            assertEquals(WeatherGlyph.FEW_CLOUDS_DAY, WeatherIcon.glyph(code))
            assertEquals(WeatherGlyph.FEW_CLOUDS_NIGHT, WeatherIcon.glyph(code + "n"))
        }
    }

    @Test
    fun dayNightAgnosticStates() {
        assertEquals(WeatherGlyph.CLOUDY, WeatherIcon.glyph("14"))
        assertEquals(WeatherGlyph.CLOUDY, WeatherIcon.glyph("15n"))
        assertEquals(WeatherGlyph.HAZE, WeatherIcon.glyph("16"))
        for (code in listOf("24", "44", "45")) assertEquals(WeatherGlyph.RAIN, WeatherIcon.glyph(code))
        for (code in listOf("25", "26", "46")) assertEquals(WeatherGlyph.HEAVY_RAIN, WeatherIcon.glyph(code))
        for (code in listOf("33", "34", "35", "36")) assertEquals(WeatherGlyph.SNOW, WeatherIcon.glyph(code))
        for (code in listOf("71", "72", "73", "74")) assertEquals(WeatherGlyph.LIGHT_SNOW, WeatherIcon.glyph(code))
        for (code in listOf("53", "54", "63", "64")) assertEquals(WeatherGlyph.THUNDER_RAIN, WeatherIcon.glyph(code))
    }

    @Test
    fun rainAndThunderHaveDayNightVariants() {
        for (code in listOf("23", "43")) {
            assertEquals(WeatherGlyph.LIGHT_RAIN_DAY, WeatherIcon.glyph(code))
            assertEquals(WeatherGlyph.LIGHT_RAIN_NIGHT, WeatherIcon.glyph(code + "n"))
        }
        for (code in listOf("51", "52", "61", "62")) {
            assertEquals(WeatherGlyph.THUNDER_DAY, WeatherIcon.glyph(code))
            assertEquals(WeatherGlyph.THUNDER_NIGHT, WeatherIcon.glyph(code + "n"))
        }
    }

    @Test
    fun unknownAndEmptyFallBack() {
        assertEquals(WeatherGlyph.FEW_CLOUDS_DAY, WeatherIcon.glyph("99"))
        assertEquals(WeatherGlyph.FEW_CLOUDS_NIGHT, WeatherIcon.glyph("99n"))
        assertEquals(WeatherGlyph.CLOUDY, WeatherIcon.glyph(null))
        assertEquals(WeatherGlyph.CLOUDY, WeatherIcon.glyph(""))
    }

    @Test
    fun isNightOverrideForcesTheVariant() {
        assertEquals(WeatherGlyph.CLEAR_NIGHT, WeatherIcon.glyph("11", isNight = true))
        assertEquals(WeatherGlyph.CLEAR_DAY, WeatherIcon.glyph("11n", isNight = false))
        // Empty code with an explicit night falls back to the clear-night glyph, not the cloud.
        assertEquals(WeatherGlyph.CLEAR_NIGHT, WeatherIcon.glyph(null, isNight = true))
        assertEquals(WeatherGlyph.CLEAR_DAY, WeatherIcon.glyph("", isNight = false))
    }
}
