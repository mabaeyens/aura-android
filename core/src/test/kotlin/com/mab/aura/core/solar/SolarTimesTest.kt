package com.mab.aura.core.solar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SolarTimesTest {

    // Madrid, midsummer. Sanity bounds only; the tight cross-language parity
    // check against SolarTimesTests.swift fixtures comes with the full port.
    @Test
    fun madridJuneHasDaytimeSunriseBeforeSunset() {
        val date = Instant.parse("2026-06-21T12:00:00Z")
        val solar = SolarTimes.compute(date, latitude = 40.4168, longitude = -3.7038)

        val sunrise = requireNotNull(solar.sunrise) { "expected a sunrise" }
        val sunset = requireNotNull(solar.sunset) { "expected a sunset" }
        assertTrue("sunrise before sunset", sunrise.isBefore(sunset))

        val zone = ZoneId.of("Europe/Madrid")
        val sunriseHour = sunrise.atZone(zone).hour
        val sunsetHour = sunset.atZone(zone).hour
        // Madrid midsummer: sunrise ~06:45, sunset ~21:45 local.
        assertTrue("sunrise in the morning", sunriseHour in 4..8)
        assertTrue("sunset in the evening", sunsetHour in 20..23)
    }

    @Test
    fun civilTwilightBracketsSunriseAndSunset() {
        val date = Instant.parse("2026-03-20T12:00:00Z")
        val solar = SolarTimes.compute(date, latitude = 40.4168, longitude = -3.7038)

        val dawn = requireNotNull(solar.civilDawn) { "expected civil dawn" }
        val dusk = requireNotNull(solar.civilDusk) { "expected civil dusk" }
        val sunrise = requireNotNull(solar.sunrise) { "expected a sunrise" }
        val sunset = requireNotNull(solar.sunset) { "expected a sunset" }
        assertTrue("dawn before sunrise", dawn.isBefore(sunrise))
        assertTrue("dusk after sunset", dusk.isAfter(sunset))
    }
}
