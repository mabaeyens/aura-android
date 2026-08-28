package com.mab.aura.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Regression, ported from the iOS `StaleLeadingDayTests`. AEMET's hourly feed can briefly lead with a stale
 * *past* day — for part of the morning its first `dia` is still yesterday, carrying only a few tail hours
 * (e.g. a sky at 20:00 with no matching temperature). The current-hour readers filter by bare hour-of-day,
 * so that yesterday-evening slot, whose hour number is still >= the current morning hour, was read as "now"
 * and pinned the hero to a slot with no temperature — blanking it to "--" across iOS, the Watch and Android
 * on 2026-08-28. `make` must anchor resolution on today and take the first upcoming hour that actually
 * carries a temperature.
 */
class StaleLeadingDayTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val json = Json { ignoreUnknownKeys = true }

    private val location = Location(ine = "28079", nombre = "Madrid", provincia = "Madrid",
        latitude = 40.4168, longitude = -3.7038)

    // 09:00 Madrid on the 28th (UTC+2 in August = 07:00 UTC). The current hour (9) is below yesterday's tail
    // hours (20–23), so the stale slots survive a bare `hour >= 9` filter. This is the shape that blanked it.
    private val morningOf28th = Instant.parse("2026-08-28T07:00:00Z")

    // A minimal daily feed for the 28th (the hero never reads its max, but make() needs a daily product).
    private fun daily(): MunicipioForecast {
        val payload = """
            [{"nombre":"Madrid","provincia":"Madrid","prediccion":{"dia":[
              {"fecha":"2026-08-28T00:00:00",
               "temperatura":{"maxima":31,"minima":16},
               "humedadRelativa":{"maxima":70,"minima":30}}
            ]}}]
        """.trimIndent()
        return json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MunicipioForecast.serializer()), payload)[0]
    }

    // Hourly feed whose dia[0] is *yesterday* (27th, tail only: a 20:00 sky with no temperature) and whose
    // dia[1] is today (28th, full morning). Mirrors the live feed on the morning of the 28th.
    private fun staleLeadingHourly(): MunicipioHourly {
        val payload = """
            {"nombre":"Madrid","provincia":"Madrid","prediccion":{"dia":[
              {"fecha":"2026-08-27T00:00:00",
               "temperatura":[{"value":"19","periodo":"21"},{"value":"18","periodo":"22"},{"value":"17","periodo":"23"}],
               "estadoCielo":[{"value":"11n","periodo":"20","descripcion":"Despejado"},{"value":"11n","periodo":"21","descripcion":"Despejado"},{"value":"11n","periodo":"22","descripcion":"Despejado"},{"value":"11n","periodo":"23","descripcion":"Despejado"}],
               "humedadRelativa":[{"value":"60","periodo":"20"},{"value":"62","periodo":"21"},{"value":"64","periodo":"22"},{"value":"66","periodo":"23"}],
               "probPrecipitacion":[{"value":"0","periodo":"2024"}]},
              {"fecha":"2026-08-28T00:00:00",
               "temperatura":[{"value":"20","periodo":"09"},{"value":"22","periodo":"10"},{"value":"24","periodo":"11"},{"value":"26","periodo":"12"},{"value":"28","periodo":"13"},{"value":"30","periodo":"14"}],
               "estadoCielo":[{"value":"11","periodo":"09","descripcion":"Despejado"},{"value":"11","periodo":"10","descripcion":"Despejado"},{"value":"11","periodo":"11","descripcion":"Despejado"},{"value":"11","periodo":"12","descripcion":"Despejado"},{"value":"11","periodo":"13","descripcion":"Despejado"},{"value":"11","periodo":"14","descripcion":"Despejado"}],
               "humedadRelativa":[{"value":"55","periodo":"09"},{"value":"50","periodo":"10"},{"value":"45","periodo":"11"},{"value":"40","periodo":"12"},{"value":"38","periodo":"13"},{"value":"35","periodo":"14"}],
               "probPrecipitacion":[{"value":"0","periodo":"0612"}]}
            ]}}
        """.trimIndent()
        return json.decodeFromString(MunicipioHourly.serializer(), payload)
    }

    @Test
    fun staleLeadingDay_doesNotBlankHero() {
        val s = WeatherSnapshot.make(location, daily(), staleLeadingHourly(), zone = madrid, now = morningOf28th)
        assertEquals(20, s.heroTemp(morningOf28th, madrid))   // today's 09:00, not "--"
        assertNotEquals(19, s.heroTemp(morningOf28th, madrid)) // must not read yesterday's 21:00 tail temperature
    }

    @Test
    fun staleLeadingDay_resolvesTodaysHumidity() {
        val s = WeatherSnapshot.make(location, daily(), staleLeadingHourly(), zone = madrid, now = morningOf28th)
        assertEquals(55, s.currentHumidity)  // today's 09:00, not the 27th's tail
    }

    // Defence-in-depth: even if today itself leads with a sky-only hour (no temperature), the hero takes the
    // first upcoming hour that carries one rather than blanking.
    @Test
    fun skyWithoutTemperature_fallsThroughToNextHour() {
        val payload = """
            {"nombre":"Madrid","provincia":"Madrid","prediccion":{"dia":[
              {"fecha":"2026-08-28T00:00:00",
               "temperatura":[{"value":"24","periodo":"11"},{"value":"26","periodo":"12"}],
               "estadoCielo":[{"value":"11","periodo":"09","descripcion":"Despejado"},{"value":"11","periodo":"10","descripcion":"Despejado"},{"value":"11","periodo":"11","descripcion":"Despejado"},{"value":"11","periodo":"12","descripcion":"Despejado"}],
               "humedadRelativa":[{"value":"55","periodo":"11"}],
               "probPrecipitacion":[{"value":"0","periodo":"0612"}]}
            ]}}
        """.trimIndent()
        val hourly = json.decodeFromString(MunicipioHourly.serializer(), payload)
        val s = WeatherSnapshot.make(location, daily(), hourly, zone = madrid, now = morningOf28th)
        assertEquals(24, s.heroTemp(morningOf28th, madrid))  // skips the 09:00/10:00 sky-only hours to the first with a temperature
    }
}
