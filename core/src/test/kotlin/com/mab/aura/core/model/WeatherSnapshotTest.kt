package com.mab.aura.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

/**
 * Pins the ported `WeatherSnapshot` logic half: the computed helpers and a persistence round-trip. There
 * is no Swift `WeatherSnapshotTests` covering these (the Swift suites exercise the deferred `make()` mapping),
 * so they pin the behaviour directly. All time-of-day tests inject `UTC` so a slot's `hour` maps straight to
 * the instant's UTC hour and the arithmetic is easy to read.
 */
class WeatherSnapshotTest {

    private val utc = ZoneId.of("UTC")
    private val day = Instant.parse("2024-01-15T00:00:00Z")
    private fun at(iso: String) = Instant.parse(iso)

    /** A minimal snapshot; every field a test cares about is passed by name. */
    private fun snapshot(
        currentTemp: Int? = null,
        tempMax: Int? = null,
        currentSky: String? = null,
        currentHumidity: Int? = null,
        windDirection: com.mab.aura.core.wind.WindDirection? = null,
        sunrise: Instant? = null,
        sunset: Instant? = null,
        hours: List<HourSlot> = emptyList(),
        alert: WeatherAlert? = null,
        updated: Instant = day,
    ) = WeatherSnapshot(
        ine = "28079", localidad = "Madrid", provincia = "Madrid",
        currentTemp = currentTemp, tempMax = tempMax, currentSky = currentSky,
        currentHumidity = currentHumidity, windDirection = windDirection,
        sunrise = sunrise, sunset = sunset, hours = hours, alert = alert, updated = updated,
    )

    // --- heroTemp / heroIsObserved ---

    @Test
    fun heroTemp_isCurrentHourForecastAndNeverFallsBackToDailyMax() {
        // No strip here, so the display-time resolver falls through to the stored currentTemp.
        val at = at("2024-01-15T12:00:00Z")
        assertEquals(14, snapshot(currentTemp = 14, tempMax = 18).heroTemp(at, utc))
        // No current-hour reading → null (the card shows "—"), never today's high: a stale/missing
        // hourly feed must not read as a real "now" temperature pinned to the day's peak.
        assertNull(snapshot(currentTemp = null, tempMax = 18).heroTemp(at, utc))
        assertNull(snapshot(currentTemp = null, tempMax = null).heroTemp(at, utc))
        assertFalse(snapshot().heroIsObserved)
    }

    @Test
    fun heroTemp_resolvesFromTheStripAtDisplayTimeNotTheFrozenScalar() {
        // A snapshot whose stored currentTemp is stale (yesterday's 5°) but whose strip carries today's
        // absolutely-timestamped hours. The hero must read the strip against `now`, not the frozen scalar.
        val today = at("2024-01-15T00:00:00Z")
        fun slot(hour: Int, temp: Int) = HourSlot(
            hour = hour, temp = temp, date = today.plusSeconds(hour * 3600L),
        )
        val s = snapshot(
            currentTemp = 5,                                   // stale scalar from a prior build
            hours = listOf(slot(9, 18), slot(10, 20), slot(11, 22)),
            updated = today,
        )
        // At 10:00 the strip re-anchors to the 10:00 slot: hero is 20, not the frozen 5.
        assertEquals(20, s.heroTemp(at("2024-01-15T10:00:00Z"), utc))
    }

    // --- hasCurrentHourData ---

    @Test
    fun hasCurrentHourData_trueWhenAnyCurrentFieldPresent() {
        assertTrue(snapshot(currentTemp = 10).hasCurrentHourData)
        assertTrue(snapshot(currentSky = "11").hasCurrentHourData)
        assertTrue(snapshot(currentHumidity = 60).hasCurrentHourData)
        assertFalse(snapshot().hasCurrentHourData)
    }

    // --- activeAlert ---

    @Test
    fun activeAlert_gatesTheStoredAlertOnStillBeingActive() {
        val now = at("2024-01-15T12:00:00Z")
        fun alert(expires: Instant?) = WeatherAlert(
            level = WeatherAlert.Level.NARANJA, event = "Aviso", phenomenon = "Lluvia",
            zona = "612801", areaDesc = "Madrid", onset = null, expires = expires,
        )
        assertEquals("Lluvia", snapshot(alert = alert(at("2024-01-15T18:00:00Z"))).activeAlert(now)?.phenomenon)
        assertNull(snapshot(alert = alert(at("2024-01-15T06:00:00Z"))).activeAlert(now)) // expired
        assertNull(snapshot(alert = null).activeAlert(now))
    }

    // --- nextSunEvent ---

    @Test
    fun nextSunEvent_prefersUpcomingSunriseThenSunsetThenFallsBackToSunrise() {
        val sr = at("2024-01-15T08:00:00Z")
        val ss = at("2024-01-15T18:00:00Z")
        val s = snapshot(sunrise = sr, sunset = ss)
        assertEquals(WeatherSnapshot.SunEvent.Sunrise(sr), s.nextSunEvent(at("2024-01-15T06:00:00Z")))
        assertEquals(WeatherSnapshot.SunEvent.Sunset(ss), s.nextSunEvent(at("2024-01-15T10:00:00Z")))
        // After dark both are past, so it falls back to today's sunrise standing in for tomorrow's.
        assertEquals(WeatherSnapshot.SunEvent.Sunrise(sr), s.nextSunEvent(at("2024-01-15T20:00:00Z")))
    }

    // --- isNight ---

    @Test
    fun isNight_beforeSunriseOrAfterSunset() {
        val s = snapshot(sunrise = at("2024-01-15T07:30:00Z"), sunset = at("2024-01-15T17:30:00Z"))
        assertFalse(s.isNight(at("2024-01-15T12:00:00Z"), utc))
        assertTrue(s.isNight(at("2024-01-15T06:00:00Z"), utc))
        assertTrue(s.isNight(at("2024-01-15T20:00:00Z"), utc))
    }

    @Test
    fun isNight_reDatesDayOldSunTimesOntoTheQueriedDay() {
        // Sun times a day old; noon on the 15th must still read as day, not night (it's after the 14th's
        // absolute sunset). This is what AuraSunPath.onSameDay fixes.
        val s = snapshot(sunrise = at("2024-01-14T07:30:00Z"), sunset = at("2024-01-14T17:30:00Z"))
        assertFalse(s.isNight(at("2024-01-15T12:00:00Z"), utc))
    }

    @Test
    fun isNight_fallsBackToTheSkyCodeSuffixWhenSunTimesAreMissing() {
        assertTrue(snapshot(currentSky = "11n").isNight(at("2024-01-15T12:00:00Z"), utc))
        assertFalse(snapshot(currentSky = "11").isNight(at("2024-01-15T12:00:00Z"), utc))
        assertFalse(snapshot(currentSky = null).isNight(at("2024-01-15T12:00:00Z"), utc))
    }

    // --- upcomingHours ---

    @Test
    fun upcomingHours_reconstructsNullDateSlotsAndDropsPastHours() {
        // A wrapping strip built at midnight: 22h, 23h (day 15), then 0h, 1h (day 16 after the wrap).
        val hours = listOf(
            HourSlot(hour = 22), HourSlot(hour = 23), HourSlot(hour = 0), HourSlot(hour = 1),
        )
        val kept = snapshot(hours = hours, updated = day)
            .upcomingHours(now = at("2024-01-15T23:00:00Z"), zone = utc)
        assertEquals(listOf(23, 0, 1), kept.map { it.hour })
    }

    @Test
    fun upcomingHours_usesStampedDatesWhenPresent() {
        val hours = listOf(
            HourSlot(hour = 9, date = at("2024-01-15T09:00:00Z")),
            HourSlot(hour = 10, date = at("2024-01-15T10:00:00Z")),
            HourSlot(hour = 11, date = at("2024-01-15T11:00:00Z")),
        )
        val kept = snapshot(hours = hours).upcomingHours(now = at("2024-01-15T10:00:00Z"), zone = utc)
        assertEquals(listOf(10, 11), kept.map { it.hour })
    }

    @Test
    fun upcomingHours_returnsTheStoredStripWhenNothingRemainsAhead() {
        val hours = listOf(HourSlot(hour = 22), HourSlot(hour = 23))
        val kept = snapshot(hours = hours, updated = day)
            .upcomingHours(now = at("2024-02-01T00:00:00Z"), zone = utc)
        assertEquals(hours, kept)
    }

    // --- persistence ---

    @Test
    fun serialization_persistsInstantsAsEpochMillisAndRoundTrips() {
        val original = snapshot(
            currentTemp = 12, tempMax = 15,
            sunrise = at("2024-01-15T07:30:00Z"), sunset = at("2024-01-15T17:30:00Z"),
            hours = listOf(HourSlot(hour = 9, temp = 10, date = at("2024-01-15T09:00:00Z"))),
        )
        val json = Json.encodeToString(WeatherSnapshot.serializer(), original)
        assertTrue(json.contains("${day.toEpochMilli()}")) // `updated` is a plain number, not an object
        assertEquals(original, Json.decodeFromString(WeatherSnapshot.serializer(), json))
    }

    @Test
    fun serialization_toleratesAnOlderSnapshotMissingOptionalKeys() {
        // Only the required keys; every optional field must default rather than throw (Codable parity).
        val json = """{"ine":"28079","localidad":"Madrid","provincia":"Madrid","updated":${day.toEpochMilli()}}"""
        val decoded = Json.decodeFromString(WeatherSnapshot.serializer(), json)
        assertEquals("Madrid", decoded.localidad)
        assertNull(decoded.tempMax)
        assertNull(decoded.sunrise)
        assertEquals(emptyList<HourSlot>(), decoded.hours)
        assertEquals(day, decoded.updated)
    }
}
