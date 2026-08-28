package com.mab.aura.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Pins the pure `StationObservation`/`Location` logic ported from `StationObservation.swift` and
 * `Location.swift`. The Swift side carries no unit tests for these, so (like `AirQualityTest`'s ICA
 * edges) these pin the ported behaviour directly: `temperature` rounding, `timestamp` parsing,
 * `stationName` title-casing, `Location.provinciaCode`, and the freshest/age/distance `nearest` rules.
 */
class StationObservationTest {

    // AEMET's observation timestamp shape, e.g. "2026-08-19T15:00:00+0000".
    private val aemet = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
    private val now: Instant = Instant.parse("2026-08-19T15:30:00Z")

    private fun fint(instant: Instant): String =
        instant.atOffset(ZoneOffset.UTC).format(aemet)

    // --- temperature ---

    @Test
    fun temperature_roundsToWholeDegrees() {
        assertEquals(23, obs(ta = 23.4).temperature)
        assertEquals(24, obs(ta = 23.6).temperature)
        assertEquals(1, obs(ta = 0.5).temperature)
    }

    @Test
    fun temperature_tiesAwayFromZeroForNegatives() {
        // Swift's `.rounded()` gives -1 for -0.5; a plain roundToInt (ties toward +∞) would give 0.
        assertEquals(-1, obs(ta = -0.5).temperature)
        assertEquals(-3, obs(ta = -2.5).temperature)
        assertNull(obs(ta = null).temperature)
    }

    // --- timestamp ---

    @Test
    fun timestamp_parsesAemetOffsetShape() {
        assertEquals(
            Instant.parse("2026-08-19T15:00:00Z"),
            obs(fint = "2026-08-19T15:00:00+0000").timestamp,
        )
    }

    @Test
    fun timestamp_nullWhenAbsentOrMalformed() {
        assertNull(obs(fint = null).timestamp)
        assertNull(obs(fint = "not a date").timestamp)
    }

    // --- stationName ---

    @Test
    fun stationName_titleCasesAllCapsUbi() {
        assertEquals("Madrid Retiro", obs(ubi = "MADRID RETIRO").stationName)
        assertNull(obs(ubi = null).stationName)
    }

    // --- Location ---

    @Test
    fun location_provinciaCodeAndId() {
        val madrid = Location(ine = "28079", nombre = "Madrid", provincia = "Madrid",
            latitude = 40.4168, longitude = -3.7038)
        assertEquals("28", madrid.provinciaCode)
        assertEquals("28079", madrid.id)
    }

    // --- nearest ---

    // Madrid centre; Retiro sits ~1.8 km away, Cuatro Vientos ~8 km, Barcelona ~500 km.
    private val madridLat = 40.4168
    private val madridLon = -3.7038

    private fun retiro(fint: String, ta: Double) =
        StationObservation(idema = "3195", ubi = "MADRID RETIRO",
            lat = 40.4152, lon = -3.6828, ta = ta, fint = fint)

    private fun cuatroVientos(fint: String, ta: Double) =
        StationObservation(idema = "3196", ubi = "CUATRO VIENTOS",
            lat = 40.3772, lon = -3.7853, ta = ta, fint = fint)

    private fun barcelona(fint: String, ta: Double) =
        StationObservation(idema = "0076", ubi = "BARCELONA",
            lat = 41.3874, lon = 2.1686, ta = ta, fint = fint)

    @Test
    fun nearest_picksClosestRecentStation() {
        val obs = listOf(
            cuatroVientos(fint(now), 25.0),
            retiro(fint(now), 24.0),
        )
        assertEquals("3195", StationObservation.nearest(madridLat, madridLon, obs, now = now)?.idema)
    }

    @Test
    fun nearest_keepsEachStationsFreshestReading() {
        // Two Retiro records: the older (14:00, 20°) must lose to the newer (15:00, 24°).
        val obs = listOf(
            retiro(fint(now.minus(Duration.ofMinutes(90))), 20.0),
            retiro(fint(now), 24.0),
        )
        assertEquals(24, StationObservation.nearest(madridLat, madridLon, obs, now = now)?.temperature)
    }

    @Test
    fun nearest_dropsReadingsOlderThanMaxAge() {
        // Only a stale Retiro reading (5.5 h old) is present, so nothing qualifies.
        val obs = listOf(retiro(fint(now.minus(Duration.ofMinutes(330))), 24.0))
        assertNull(StationObservation.nearest(madridLat, madridLon, obs, now = now))
    }

    @Test
    fun nearest_dropsStationsBeyondMaxDistance() {
        // A recent Barcelona reading is ~500 km away, outside the 20 km cutoff.
        val obs = listOf(barcelona(fint(now), 26.0))
        assertNull(StationObservation.nearest(madridLat, madridLon, obs, now = now))
    }

    @Test
    fun nearest_extensionForwardsLocationAndUsesNow() {
        val madrid = Location(ine = "28079", nombre = "Madrid", provincia = "Madrid",
            latitude = madridLat, longitude = madridLon)
        // The extension uses the default now = Instant.now(), so stamp the reading at the real now.
        val fresh = retiro(fint(Instant.now()), 24.0)
        assertEquals("3195", listOf(fresh).nearest(to = madrid)?.idema)
    }

    // --- reading (surface values in display units) ---

    @Test
    fun reading_convertsWindToKmhAndSnapsDirection() {
        // AEMET reports station wind in m/s; the app shows km/h (×3.6). 10 m/s → 36 km/h; 90° → E.
        val full = StationObservation(idema = "3195", ubi = "MADRID RETIRO", lat = 40.4, lon = -3.7,
            ta = 23.6, hr = 55.4, vv = 10.0, dv = 90.0, pres = 1013.6, prec = 0.2, fint = fint(now))
        val r = full.reading
        assertEquals(24, r.temperature)
        assertEquals(55, r.humidity)
        assertEquals(36, r.windKmh)
        assertEquals(com.mab.aura.core.wind.WindDirection.E, r.windDirection)
        assertEquals(1014, r.pressure)
        assertEquals(0.2, r.precipMm!!, 1e-9)
    }

    @Test
    fun reading_nullsMetricsTheStationOmits() {
        // A temperature-only station leaves every other reading field null.
        val r = StationObservation(idema = "x", ubi = "X", lat = 40.4, lon = -3.7, ta = 20.0, fint = fint(now)).reading
        assertEquals(20, r.temperature)
        assertNull(r.humidity)
        assertNull(r.windKmh)
        assertNull(r.windDirection)
        assertNull(r.pressure)
        assertNull(r.precipMm)
    }

    // --- availableMetrics ---

    @Test
    fun availableMetrics_flagsOnlyReportedFields() {
        val partial = StationObservation(idema = "x", ubi = "X", lat = 40.4, lon = -3.7,
            ta = 20.0, hr = 50.0, dv = 90.0, fint = fint(now))   // temp + humidity + a direction but no speed
        val m = partial.availableMetrics
        assertEquals(true, m.contains(ObservedMetrics.TEMPERATURE))
        assertEquals(true, m.contains(ObservedMetrics.HUMIDITY))
        // A direction (dv) with no speed (vv) must NOT count as measuring wind.
        assertEquals(false, m.contains(ObservedMetrics.WIND))
        assertEquals(false, m.contains(ObservedMetrics.PRESSURE))
        assertEquals(false, m.contains(ObservedMetrics.PRECIPITATION))
    }

    @Test
    fun availableMetrics_allFiveWhenFullyReported() {
        val full = StationObservation(idema = "x", ubi = "X", lat = 40.4, lon = -3.7,
            ta = 20.0, hr = 50.0, vv = 3.0, dv = 90.0, pres = 1010.0, prec = 0.0, fint = fint(now))
        val all = ObservedMetrics.TEMPERATURE or ObservedMetrics.WIND or ObservedMetrics.HUMIDITY or
            ObservedMetrics.PRESSURE or ObservedMetrics.PRECIPITATION
        assertEquals(true, full.availableMetrics.contains(all))
    }

    // --- distanceKm ---

    @Test
    fun distanceKm_measuresToTheStationAndNullsWithoutCoords() {
        val madrid = Location(ine = "28079", nombre = "Madrid", provincia = "Madrid",
            latitude = madridLat, longitude = madridLon)
        // Retiro sits ~1.8 km from Madrid centre.
        val km = retiro(fint(now), 24.0).distanceKm(to = madrid)!!
        assertEquals(1.8, km, 0.5)
        // A reading with no coordinates can't be placed.
        assertNull(StationObservation(idema = "x", ubi = "X", lat = null, lon = null, ta = 20.0).distanceKm(to = madrid))
    }

    private fun obs(
        ta: Double? = null,
        fint: String? = null,
        ubi: String? = null,
    ) = StationObservation(idema = "0000", ubi = ubi, lat = null, lon = null, ta = ta, fint = fint)
}
