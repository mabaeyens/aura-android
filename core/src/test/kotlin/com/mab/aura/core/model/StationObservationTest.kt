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
        // A recent Barcelona reading is ~500 km away, outside the 35 km cutoff.
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

    private fun obs(
        ta: Double? = null,
        fint: String? = null,
        ubi: String? = null,
    ) = StationObservation(idema = "0000", ubi = ubi, lat = null, lon = null, ta = ta, fint = fint)
}
