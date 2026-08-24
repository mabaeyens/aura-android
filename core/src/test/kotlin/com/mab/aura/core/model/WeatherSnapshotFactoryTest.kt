package com.mab.aura.core.model

import com.mab.aura.core.wind.WindDirection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }
}
