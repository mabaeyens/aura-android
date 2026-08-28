package com.mab.aura.core.model

import com.mab.aura.core.wind.WindDirection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Pins `WeatherSnapshot.make()` and its AEMET-mapping helpers, ported from `WeatherSnapshot.swift`. The
 * Swift `WindGustTests` (via `slots`) and the `precipAmount` case from `ForecastPhraseTests` port directly;
 * the end-to-end make() mapping has no single Swift test, so it's pinned here against constructed forecasts.
 */
class WeatherSnapshotFactoryTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val json = Json { ignoreUnknownKeys = true }

    // --- precipAmount (ported from ForecastPhraseTests.testPrecipAmountParsing) ---

    @Test
    fun precipAmount_parsesTraceCommaAndBlanks() {
        assertEquals(0.0, WeatherSnapshot.precipAmount("0")!!, 1e-9)
        assertEquals(0.4, WeatherSnapshot.precipAmount("0.4")!!, 1e-9)
        assertEquals(1.2, WeatherSnapshot.precipAmount("1,2")!!, 1e-9)   // decimal comma
        assertEquals(0.0, WeatherSnapshot.precipAmount("Ip")!!, 1e-9)    // trace reads as 0
        assertNull(WeatherSnapshot.precipAmount(""))
        assertNull(WeatherSnapshot.precipAmount("—"))
    }

    // --- slots wind/gust extraction (ported from WindGustTests) ---

    // One Dia with mixed wind + gust entries at 10h and 11h, plus an 11h with no gust.
    private fun decodeDia(): MunicipioHourly.Dia {
        val payload = """
            {
              "fecha": "2026-08-21T00:00:00",
              "temperatura": [{"value":"22","periodo":"10"},{"value":"24","periodo":"11"}],
              "estadoCielo": [{"value":"11","periodo":"10"},{"value":"11","periodo":"11"}],
              "humedadRelativa": [{"value":"50","periodo":"10"},{"value":"48","periodo":"11"}],
              "probPrecipitacion": [{"value":"0","periodo":"0612"}],
              "vientoAndRachaMax": [
                {"direccion":["NO"],"velocidad":["20"],"periodo":"10"},
                {"value":"45","periodo":"10"},
                {"direccion":["N"],"velocidad":["15"],"periodo":"11"}
              ]
            }
        """.trimIndent()
        return json.decodeFromString(MunicipioHourly.Dia.serializer(), payload)
    }

    @Test
    fun slots_surfaceGustPerHour() {
        val slots = WeatherSnapshot.slots(decodeDia(), madrid)
        val h10 = slots.first { it.hour == 10 }
        val h11 = slots.first { it.hour == 11 }
        assertEquals(20, h10.windSpeed)
        assertEquals(45, h10.windGust)   // the 10h gust is surfaced alongside its wind speed
        assertEquals(15, h11.windSpeed)
        assertNull(h11.windGust)         // no gust reported at 11h stays null, not 0
    }

    // A dia carrying the whole current-conditions family, so buildSlots must stamp every field WeatherSnapshot
    // .resolved() re-derives from the strip — not just temp/sky/precipProb/wind.
    private fun fullFamilyDia(): MunicipioHourly.Dia {
        val payload = """
            {
              "fecha": "2026-08-21T00:00:00",
              "temperatura": [{"value":"22","periodo":"10"},{"value":"24","periodo":"11"}],
              "estadoCielo": [{"value":"11","periodo":"10","descripcion":"Despejado"},{"value":"12","periodo":"11","descripcion":"Poco nuboso"}],
              "humedadRelativa": [{"value":"50","periodo":"10"},{"value":"48","periodo":"11"}],
              "probPrecipitacion": [{"value":"30","periodo":"0812"}],
              "sensTermica": [{"value":"23","periodo":"10"},{"value":"26","periodo":"11"}],
              "precipitacion": [{"value":"0","periodo":"10"},{"value":"1,2","periodo":"11"}],
              "nieve": [{"value":"Ip","periodo":"10"}],
              "probTormenta": [{"value":"15","periodo":"0812"}],
              "vientoAndRachaMax": [
                {"direccion":["NO"],"velocidad":["20"],"periodo":"10"},
                {"direccion":["N"],"velocidad":["15"],"periodo":"11"}
              ]
            }
        """.trimIndent()
        return json.decodeFromString(MunicipioHourly.Dia.serializer(), payload)
    }

    @Test
    fun slots_stampTheWholeCurrentFamilyPerHour() {
        val slots = WeatherSnapshot.slots(fullFamilyDia(), madrid)
        val h10 = slots.first { it.hour == 10 }
        val h11 = slots.first { it.hour == 11 }
        assertEquals("Despejado", h10.skyText)
        assertEquals("Poco nuboso", h11.skyText)
        assertEquals(50, h10.humidity)
        assertEquals(23, h10.feelsLike)
        assertEquals(26, h11.feelsLike)
        assertEquals(0.0, h10.precipMm)          // "0" reads as dry
        assertEquals(1.2, h11.precipMm)          // decimal comma parses
        assertEquals(0.0, h10.snowMm)            // "Ip" (trace) reads as 0
        assertNull(h11.snowMm)                   // no snow reported at 11h stays null
        assertEquals(15, h10.stormProb)          // from the coarse 08-12 block
        assertEquals(15, h11.stormProb)
        assertEquals(WindDirection.fromAemet("NO"), h10.windDirection)
        assertEquals(WindDirection.fromAemet("N"), h11.windDirection)
    }

    // --- make() end-to-end mapping ---

    private val location = Location(ine = "28079", nombre = "Madrid", provincia = "Madrid",
        latitude = 40.4168, longitude = -3.7038)

    // 09:30 UTC in August = 11:30 in Madrid (UTC+2), so the current hour is 11.
    private val now = Instant.parse("2026-08-21T09:30:00Z")

    private fun daily() = MunicipioForecast(
        nombre = "Madrid", provincia = "Madrid",
        prediccion = MunicipioForecast.Prediccion(
            dia = listOf(
                MunicipioForecast.Dia(
                    fecha = "2026-08-21",
                    temperatura = MunicipioForecast.MinMax(maxima = 30, minima = 18),
                    humedadRelativa = MunicipioForecast.MinMax(maxima = 70, minima = 30),
                    estadoCielo = listOf(MunicipioForecast.SkyBlock(value = "12", periodo = "00-24")),
                    viento = listOf(MunicipioForecast.WindBlock(velocidad = 15, periodo = "00-24")),
                    probPrecipitacion = listOf(
                        MunicipioForecast.ProbBlock(value = 20, periodo = "00-24"),
                        MunicipioForecast.ProbBlock(value = 55, periodo = "12-24"),
                    ),
                ),
            ),
        ),
    )

    private fun hourly() = MunicipioHourly(
        nombre = "Madrid", provincia = "Madrid",
        prediccion = MunicipioHourly.Prediccion(
            dia = listOf(
                MunicipioHourly.Dia(
                    fecha = "2026-08-21T00:00:00",
                    temperatura = hv("24" to "11", "25" to "12"),
                    estadoCielo = listOf(
                        MunicipioHourly.SkyValue(value = "11", periodo = "11", descripcion = "Despejado"),
                        MunicipioHourly.SkyValue(value = "12", periodo = "12", descripcion = "Poco nuboso"),
                    ),
                    humedadRelativa = hv("50" to "11", "48" to "12"),
                    probPrecipitacion = hv("30" to "0812"),
                    sensTermica = hv("26" to "11"),
                    precipitacion = hv("0" to "11"),
                    nieve = hv("Ip" to "11"),
                    probTormenta = hv("10" to "0812"),
                    vientoAndRachaMax = listOf(
                        MunicipioHourly.WindValue(periodo = "11", direccion = listOf("NO"), velocidad = listOf("20")),
                        MunicipioHourly.WindValue(periodo = "11", value = "45"),
                    ),
                ),
            ),
        ),
    )

    private fun hv(vararg pairs: Pair<String, String>) =
        pairs.map { MunicipioHourly.HourValue(value = it.first, periodo = it.second) }

    @Test
    fun make_mapsHeadlineDailyAndCurrentHourFields() {
        val observed = StationObservation(idema = "3195", ubi = "MADRID RETIRO",
            lat = 40.41, lon = -3.68, ta = 23.6, fint = "2026-08-21T11:00:00+0000")

        val s = WeatherSnapshot.make(location, daily(), hourly(), observed = observed, zone = madrid, now = now)

        // Headline / identity
        assertEquals("28079", s.ine)
        assertEquals("Madrid", s.localidad)
        assertEquals(18, s.tempMin)
        assertEquals(30, s.tempMax)
        assertEquals(70, s.humedadMax)
        assertEquals(40.4168, s.latitude!!, 1e-9)

        // Current hour (11), resolved from the hourly feed
        assertEquals(24, s.currentTemp)
        assertEquals("11", s.currentSky)
        assertEquals("Despejado", s.currentSkyText)
        assertEquals(50, s.currentHumidity)
        assertEquals(30, s.currentPrecipProb)     // block "0812" covers hour 11
        assertEquals(0.0, s.currentPrecipMm!!, 1e-9)
        assertEquals(0.0, s.currentSnowMm!!, 1e-9) // "Ip" trace → 0
        assertEquals(26, s.currentFeelsLike)
        assertEquals(10, s.currentStormProb)
        assertEquals(20, s.windSpeed)
        assertEquals(WindDirection.fromAemet("NO"), s.windDirection)
        assertEquals(45, s.windGust)

        // Observed station passthrough
        assertEquals(24, s.observedTemp)           // 23.6 rounds to 24
        assertEquals("Madrid Retiro", s.observedStation)

        // Sun times computed on-device
        assertNotNull(s.sunrise)
        assertNotNull(s.sunset)
        assertEquals(now, s.updated)

        // Days: today's sky follows the current hour; probPrecip is the max across blocks
        assertEquals(1, s.days.size)
        assertEquals("11", s.days[0].sky)          // currentSky, not the daily "12" block
        assertEquals(30, s.days[0].max)
        assertEquals(55, s.days[0].probPrecip)
        assertEquals(15, s.days[0].windSpeed)
        assertEquals(Instant.parse("2026-08-21T12:00:00Z"), s.days[0].date)   // noon UTC

        // Hourly strip re-anchored to the current hour
        assertEquals(11, s.hours.first().hour)
        assertTrue(s.hours.all { it.hour >= 11 })
    }

    // --- observation carry-forward (ported from ObservationCarryForwardTests) ---
    // When a refresh skips the hourly /observacion/convencional/todas fetch (the feed isn't due yet, or a
    // transient error left `observed` null), make() must keep the last good station reading from the prior
    // snapshot instead of blanking the observed card. A fresh reading, when present, always wins.

    @Test
    fun make_skippedObservationCarriesForwardPreviousReading() {
        // fint sits 30 min before `now` (09:30Z), so the reading is within OBSERVATION_MAX_AGE (3 h) and the
        // bounded carry-forward keeps it. A future or >3 h-old reading would be blanked instead — see
        // ObservationCardStalenessTest for those boundaries.
        val observed = StationObservation(idema = "3195", ubi = "MADRID RETIRO",
            lat = 40.41, lon = -3.68, ta = 21.4, hr = 50.0, fint = "2026-08-21T09:00:00+0000")
        val previous = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = observed, zone = madrid, now = now)
        assertEquals(21, previous.observedTemp)
        assertNotNull(previous.observedReading)

        // No fresh observation this cycle (fetch skipped), but a prior snapshot exists: reuse it wholesale.
        val rebuilt = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = null, previousObserved = previous, zone = madrid, now = now)

        assertEquals(previous.observedTemp, rebuilt.observedTemp)
        assertEquals(previous.observedStation, rebuilt.observedStation)
        assertEquals(previous.observedStationDistanceKm, rebuilt.observedStationDistanceKm)
        assertEquals(previous.observedMetrics, rebuilt.observedMetrics)
        assertEquals(previous.observedReading, rebuilt.observedReading)
    }

    @Test
    fun make_freshObservationWinsOverPrevious() {
        val previous = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = StationObservation(idema = "3195", ubi = "MADRID RETIRO",
                lat = 40.41, lon = -3.68, ta = 21.4, fint = "2026-08-21T11:00:00+0000"),
            zone = madrid, now = now)
        val fresh = StationObservation(idema = "3196", ubi = "GETAFE",
            lat = 40.30, lon = -3.72, ta = 25.0, fint = "2026-08-21T12:00:00+0000")

        val rebuilt = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = fresh, previousObserved = previous, zone = madrid, now = now)

        // The new station's reading is used wholesale — never a mix of a new temp with the old station name.
        assertEquals(25, rebuilt.observedTemp)
        assertEquals("Getafe", rebuilt.observedStation)
        assertNotEquals(previous.observedStation, rebuilt.observedStation)
    }

    @Test
    fun make_noObservationAndNoPreviousLeavesReadingNull() {
        val rebuilt = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = null, previousObserved = null, zone = madrid, now = now)
        assertNull(rebuilt.observedTemp)
        assertNull(rebuilt.observedStation)
        assertNull(rebuilt.observedReading)
        assertEquals(ObservedMetrics(), rebuilt.observedMetrics)
    }

    // The carry-forward is bounded by the age gate (OBSERVATION_MAX_AGE, 3 h): a prior reading that has aged
    // past it is dropped, not carried, so a fetch-less refresh can never resurrect a stale measurement. This
    // is the make()-time half of the gate; the card also re-checks at display time (ObservationCardStalenessTest).
    @Test
    fun make_staleObservationIsNotCarriedForward() {
        // 3.5 h before `now` (09:30Z) — past the 3 h gate.
        val previous = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = StationObservation(idema = "3195", ubi = "MADRID RETIRO",
                lat = 40.41, lon = -3.68, ta = 21.4, fint = "2026-08-21T06:00:00+0000"),
            zone = madrid, now = now)
        assertEquals(21, previous.observedTemp)   // the fresh fetch that built `previous` trusted it

        val rebuilt = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = null, previousObserved = previous, zone = madrid, now = now)

        assertNull(rebuilt.observedTemp)
        assertNull(rebuilt.observedStation)
        assertNull(rebuilt.observedReading)
        assertNull(rebuilt.observedAt)
        assertEquals(ObservedMetrics(), rebuilt.observedMetrics)
    }

    @Test
    fun make_futureObservationIsNotCarriedForward() {
        // A clock-skewed future reading (11:00Z vs 09:30Z now) is never fresh, so it is dropped, not carried.
        val previous = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = StationObservation(idema = "3195", ubi = "MADRID RETIRO",
                lat = 40.41, lon = -3.68, ta = 21.4, fint = "2026-08-21T11:00:00+0000"),
            zone = madrid, now = now)

        val rebuilt = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = null, previousObserved = previous, zone = madrid, now = now)

        assertNull(rebuilt.observedTemp)
        assertNull(rebuilt.observedAt)
    }

    @Test
    fun make_withoutHourlyIsThinButKeepsDailyAndSun() {
        val s = WeatherSnapshot.make(location, daily(), hourly = null, zone = madrid, now = now)

        // Daily headline still present…
        assertEquals(30, s.tempMax)
        assertEquals(1, s.days.size)
        assertEquals("12", s.days[0].sky)          // falls back to the daily block when there's no hourly
        assertNotNull(s.sunrise)

        // …but the current-hour and strip fields are empty (a "thin" snapshot).
        assertNull(s.currentTemp)
        assertNull(s.windSpeed)
        assertTrue(s.hours.isEmpty())
        assertFalse(s.hasCurrentHourData)
        assertNull(s.heroTemp(now, madrid))   // hero shows "—", never today's daily max
    }

    // --- hourly carry-forward ---
    // When this cycle's `horaria` fetch fails or returns nothing (`hourly` null), make() must hold the last
    // good current-hour reading from the prior snapshot rather than blanking every current* field — which
    // silently dropped the hero to today's daily max and defaulted the sky to a bare sun ("29 and clear").
    // A fresh feed, when present, always wins over the carried values.

    @Test
    fun make_hourlyFailureCarriesForwardCurrentHour() {
        val previous = WeatherSnapshot.make(location, daily(), hourly(), zone = madrid, now = now)
        assertEquals(24, previous.currentTemp)
        assertEquals("11", previous.currentSky)

        val rebuilt = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = null, previousObserved = previous, zone = madrid, now = now)

        assertEquals(previous.currentTemp, rebuilt.currentTemp)
        assertEquals(previous.currentSky, rebuilt.currentSky)
        assertEquals(previous.currentSkyText, rebuilt.currentSkyText)
        assertEquals(previous.currentHumidity, rebuilt.currentHumidity)
        assertEquals(previous.windSpeed, rebuilt.windSpeed)
        assertEquals(previous.currentTemp, rebuilt.heroTemp(now, madrid))   // hero is the carried reading, not tempMax
        assertTrue(rebuilt.hasCurrentHourData)                 // carried data must not read as thin
    }

    // The strip itself must carry forward too, not just the current-hour scalars. Before this the hourly card
    // blanked on a transient miss, and once the hero resolved from the strip it removed the very data
    // heroTemp(now) re-anchors from. The carried slots keep their absolute timestamps.
    @Test
    fun make_hourlyFailureCarriesForwardTheStrip() {
        val previous = WeatherSnapshot.make(location, daily(), hourly(), zone = madrid, now = now)
        assertTrue(previous.hours.isNotEmpty())

        val rebuilt = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = null, previousObserved = previous, zone = madrid, now = now)

        assertEquals(previous.hours, rebuilt.hours)            // the whole strip is held, not emptied
        assertEquals(previous.heroTemp(now, madrid), rebuilt.heroTemp(now, madrid))  // hero still resolves from it
    }

    // A true cold start (no prior snapshot) with no hourly feed stays honestly thin: nothing to carry, an
    // empty strip. This is the one case that legitimately renders "--", corrected by a forced retry, not carry.
    @Test
    fun make_hourlyFailureWithoutPreviousLeavesStripEmpty() {
        val rebuilt = WeatherSnapshot.make(location, daily(), hourly = null,
            observed = null, previousObserved = null, zone = madrid, now = now)

        assertTrue(rebuilt.hours.isEmpty())
        assertFalse(rebuilt.hasCurrentHourData)
    }

    @Test
    fun make_freshHourlyWinsOverCarry() {
        val fresh = WeatherSnapshot.make(location, daily(), hourly(), zone = madrid, now = now)
        assertEquals(24, fresh.currentTemp)

        // A prior snapshot with a deliberately divergent current-hour reading, which must be ignored.
        val stale = WeatherSnapshot(ine = "28079", localidad = "Madrid", provincia = "Madrid",
            currentTemp = 99, currentSky = "99", currentSkyText = "Inventado", updated = now)
        val withStale = WeatherSnapshot.make(location, daily(), hourly(),
            observed = null, previousObserved = stale, zone = madrid, now = now)

        assertEquals(fresh.currentTemp, withStale.currentTemp)
        assertNotEquals(99, withStale.currentTemp)
        assertEquals(fresh.currentSky, withStale.currentSky)
    }

    // --- current-hour description follows the day the current slot came from ---
    // When day 0's hours are all past, the resolved current hour rolls into day 1, and its sky *text* must be
    // read from day 1 too. The old code tried day 0 first at the same hour number and only fell through to
    // day 1 when day 0 had no entry — so a day 0 entry that happened to exist there won, describing a
    // different day than the sky code the glyph and background use ("Nubes altas" over a clear sky).

    // Day 0 holds only past hours (6, 7) plus an hour-0 trap with a wrong-day description; day 1 is a normal
    // early strip whose hour 0 says something else.
    private fun rolloverHourly() = MunicipioHourly(
        nombre = "Madrid", provincia = "Madrid",
        prediccion = MunicipioHourly.Prediccion(
            dia = listOf(
                MunicipioHourly.Dia(
                    fecha = "2026-08-21T00:00:00",
                    temperatura = hv("20" to "06", "20" to "07"),
                    estadoCielo = listOf(
                        MunicipioHourly.SkyValue(value = "11", periodo = "00", descripcion = "Despejado dia0"),
                        MunicipioHourly.SkyValue(value = "11", periodo = "06", descripcion = "Despejado dia0"),
                        MunicipioHourly.SkyValue(value = "11", periodo = "07", descripcion = "Despejado dia0"),
                    ),
                    humedadRelativa = emptyList(),
                    probPrecipitacion = emptyList(),
                ),
                MunicipioHourly.Dia(
                    fecha = "2026-08-22T00:00:00",
                    temperatura = hv("18" to "00", "18" to "01"),
                    estadoCielo = listOf(
                        MunicipioHourly.SkyValue(value = "17", periodo = "00", descripcion = "Nubes altas"),
                        MunicipioHourly.SkyValue(value = "17", periodo = "01", descripcion = "Nubes altas"),
                    ),
                    humedadRelativa = emptyList(),
                    probPrecipitacion = emptyList(),
                ),
            ),
        ),
    )

    @Test
    fun make_currentTextFollowsTheDayTheCurrentHourCameFrom() {
        // 08:00 UTC = 10:00 Madrid; day 0's hours (6, 7) are all past, so the current hour rolls into day 1.
        val nowRoll = Instant.parse("2026-08-21T08:00:00Z")
        val s = WeatherSnapshot.make(location, daily(), rolloverHourly(), zone = madrid, now = nowRoll)
        assertEquals("17", s.currentSky)                     // sky code is day 1's current hour
        assertEquals("Nubes altas", s.currentSkyText)        // text must come from the same day as the code
        assertNotEquals("Despejado dia0", s.currentSkyText)  // day 0's same-hour text must not leak in
    }

    @Test
    fun make_currentTextStaysOnDayZeroWhileItHasUpcomingHours() {
        // 04:00 UTC = 06:00 Madrid; day 0 still has hour 6 ahead, so the text stays day 0's.
        val nowEarly = Instant.parse("2026-08-21T04:00:00Z")
        val s = WeatherSnapshot.make(location, daily(), rolloverHourly(), zone = madrid, now = nowEarly)
        assertEquals("11", s.currentSky)
        assertEquals("Despejado dia0", s.currentSkyText)
    }
}
