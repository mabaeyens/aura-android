package com.mab.aura.core.text

import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.wind.WindDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Ported from `ForecastPhraseTests.swift`. Two families of assertions: accuracy (every known number reaches
 * the prose, and nothing false leaks in) and determinism/variety (same input reproduces, day and place
 * vary). The Swift test reads the hour off `Calendar.current`; here the zone is passed explicitly so the
 * time-of-day bucket is the same on any machine. `precipAmount` parsing is already pinned in
 * `WeatherSnapshotFactoryTest`, so it isn't repeated here.
 */
class ForecastPhraseTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    // Swift's `Date(timeIntervalSinceReferenceDate: 800_000_000)`: 2001-01-01 UTC is epoch 978_307_200.
    private val noon = Instant.ofEpochSecond(978_307_200L + 800_000_000L)

    private fun snap(
        sky: String? = "11", min: Int? = 15, max: Int? = 24,
        humidity: Int? = 63, precip: Int? = 10,
        wind: Int? = 9, dir: WindDirection? = WindDirection.O,
        mm: Double? = null, snow: Double? = null, feels: Int? = null, storm: Int? = null,
        city: String = "Bilbao",
    ) = WeatherSnapshot(
        ine = "48020", localidad = city, provincia = "Bizkaia",
        tempMin = min, tempMax = max, humedadMax = humidity,
        currentTemp = 21, currentSky = sky, currentSkyText = "Nuboso",
        currentHumidity = humidity, currentPrecipProb = precip,
        currentPrecipMm = mm, currentSnowMm = snow,
        currentFeelsLike = feels, currentStormProb = storm,
        windSpeed = wind, windDirection = dir,
        sunrise = null, sunset = null, updated = Instant.EPOCH,
    )

    private fun day(n: Int) = noon.plusSeconds(n * 86_400L)

    // --- Accuracy: the data can never be misstated ---

    @Test
    fun datalineCarriesEveryKnownNumber() {
        val line = ForecastPhrase.dataline(snap(min = 15, max = 24, humidity = 63, precip = 10,
            wind = 9, dir = WindDirection.O), noon)
        assertTrue(line, line.contains("15°"))
        assertTrue(line, line.contains("24°"))
        assertTrue(line, line.contains("9 km/h"))
        assertTrue(line, line.lowercase().contains("oeste"))
        assertTrue(line, line.contains("63%"))
        assertTrue(line, line.contains("10%"))
    }

    @Test
    fun datalineDryDaySaysSoAndShowsNoRainPercent() {
        // precip 0 → an explicit dry sentence, and no "0%" rain figure leaks in.
        for (d in 0 until 20) {
            val line = ForecastPhrase.dataline(snap(precip = 0), day(d))
            assertFalse(line, line.contains("0%"))
            val dry = listOf("sin lluvia", "no se espera lluvia", "jornada seca")
            assertTrue(line, dry.any { line.lowercase().contains(it) })
        }
    }

    @Test
    fun datalineDegradesWhenFieldsMissing() {
        // No temps, no humidity, only wind + rain — still a clean sentence, no dangling punctuation.
        val line = ForecastPhrase.dataline(
            snap(min = null, max = null, humidity = null, precip = 40, wind = 12, dir = WindDirection.NE), noon)
        assertFalse(line.isEmpty())
        assertFalse(line, line.contains(" ,"))
        assertFalse(line, line.contains("..."))
        assertTrue(line, line.contains("40%"))
    }

    // --- Rain amount (mm), feels-like, storm ---

    @Test
    fun datalineShowsRainAmountWhenMeaningful() {
        val line = ForecastPhrase.dataline(snap(precip = 75, mm = 2.0), noon)
        assertTrue(line, line.contains("75%"))
        assertTrue(line, line.contains("2 mm"))   // whole number, no decimal
    }

    @Test
    fun datalineFormatsFractionalMmWithComma() {
        val line = ForecastPhrase.dataline(snap(precip = 60, mm = 0.4), noon)
        assertTrue(line, line.contains("0,4 mm"))
        assertFalse(line, line.contains("0.4"))   // Spanish decimal comma, never a dot
    }

    @Test
    fun datalineOmitsTraceAndZeroMm() {
        for (amount in listOf(0.0, 0.05)) {
            val line = ForecastPhrase.dataline(snap(precip = 20, mm = amount), noon)
            assertFalse("$amount: $line", line.lowercase().contains("mm"))
        }
    }

    @Test
    fun datalineShowsSnowAmountOnSnowyDays() {
        val line = ForecastPhrase.dataline(snap(sky = "34", precip = 80, snow = 3.0), noon)  // 34 = snow
        assertTrue(line, line.lowercase().contains("3 mm de nieve"))
    }

    @Test
    fun datalineOmitsTraceSnow() {
        val line = ForecastPhrase.dataline(snap(snow = 0.0), noon)
        assertFalse(line, line.lowercase().contains("nieve"))
    }

    @Test
    fun datalineShowsStormRiskWhenLikely() {
        val line = ForecastPhrase.dataline(snap(precip = 40, storm = 55), noon)
        assertTrue(line, line.lowercase().contains("tormenta"))
        assertTrue(line, line.contains("55%"))
    }

    @Test
    fun datalineShowsFeelsLikeOnlyWhenItDiverges() {
        // currentTemp is 21 in the helper; 30 diverges (≥3) → shown, 22 does not.
        val diverges = ForecastPhrase.dataline(snap(feels = 30), noon)
        assertTrue(diverges, diverges.lowercase().contains("sensación de 30°"))
        for (d in 0 until 12) {
            val close = ForecastPhrase.dataline(snap(feels = 22), day(d))
            assertFalse(close, close.lowercase().contains("sensación"))
        }
    }

    // --- Headline rules ---

    @Test
    fun headlineNamesRainWhenRainy() {
        val line = ForecastPhrase.headline(snap(sky = "26"), noon, madrid)  // 26 = rain
        assertTrue(line, line.lowercase().contains("lluvi") || line.lowercase().contains("chubasc"))
    }

    @Test
    fun headlineMentionsWindOnlyWhenNoticeable() {
        // Force 4 (25 km/h) from the NE → the direction is named.
        val windy = ForecastPhrase.headline(snap(sky = "11", wind = 25, dir = WindDirection.NE), noon, madrid)
        assertTrue(windy, windy.lowercase().contains("nordeste"))
        // Near-calm (3 km/h, force 1) → no wind direction in the headline.
        for (d in 0 until 12) {
            val calm = ForecastPhrase.headline(snap(sky = "11", wind = 3, dir = WindDirection.NE), day(d), madrid)
            assertFalse(calm, calm.lowercase().contains("del "))
        }
    }

    @Test
    fun headlineIsAProperSentence() {
        val line = ForecastPhrase.headline(snap(), noon, madrid)
        assertTrue(line, line.first().isUpperCase())
        assertTrue(line, line.endsWith("."))
    }

    // --- Determinism + variety ---

    @Test
    fun sameInputIsReproducible() {
        val a = ForecastPhrase.headline(snap(), noon, madrid)
        val b = ForecastPhrase.headline(snap(), noon, madrid)
        assertTrue(a == b)
    }

    @Test
    fun variesDayToDay() {
        val seen = mutableSetOf<String>()
        for (d in 0 until 14) {
            seen.add(ForecastPhrase.headline(snap(), day(d), madrid))
        }
        assertTrue("expected several distinct phrasings across a fortnight, got $seen", seen.size > 2)
    }

    @Test
    fun variesByLocation() {
        val bilbao = ForecastPhrase.dataline(snap(city = "Bilbao"), noon) +
            ForecastPhrase.headline(snap(city = "Bilbao"), noon, madrid)
        val sevilla = ForecastPhrase.dataline(snap(city = "Sevilla"), noon) +
            ForecastPhrase.headline(snap(city = "Sevilla"), noon, madrid)
        // Same data, different town — the seed should reshape at least one of the two lines.
        assertNotEquals(bilbao, sevilla)
    }
}
