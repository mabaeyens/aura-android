package com.mab.aura.core.solar

import java.time.Instant
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Sunrise/sunset and civil twilight from the NOAA sunrise equation.
 *
 * Direct port of `SolarTimes.swift` in AuraKit. Deterministic and offline:
 * the same latitude/longitude/instant must yield the same result as the Swift
 * version (see [SolarTimesTest] and, later, a cross-language parity test against
 * the fixtures in Tests/AuraKitTests/SolarTimesTests.swift).
 */
data class SolarTimes(
    val sunrise: Instant?,
    val sunset: Instant?,
    val civilDawn: Instant?,
    val civilDusk: Instant?,
) {
    companion object {
        private const val RAD = Math.PI / 180.0

        fun compute(date: Instant, latitude: Double, longitude: Double): SolarTimes {
            val julianDate = date.epochSecond / 86_400.0 + 2_440_587.5
            val n = (julianDate - 2_451_545.0 + 0.0008).roundToLong().toDouble()

            val westLongitude = -longitude
            val meanSolarTime = n + westLongitude / 360.0

            val meanAnomaly = (357.5291 + 0.98560028 * meanSolarTime).mod(360.0)
            val m = meanAnomaly * RAD
            val center = 1.9148 * sin(m) + 0.0200 * sin(2 * m) + 0.0003 * sin(3 * m)
            val eclipticLongitude = (meanAnomaly + center + 180 + 102.9372).mod(360.0)
            val lambda = eclipticLongitude * RAD

            val transit = 2_451_545.0 + meanSolarTime + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)
            val declination = asin(sin(lambda) * sin(23.4397 * RAD))
            val phi = latitude * RAD

            val (sunrise, sunset) = crossings(altitudeDeg = -0.833, transit, phi, declination)
            val (dawn, dusk) = crossings(altitudeDeg = -6.0, transit, phi, declination)
            return SolarTimes(sunrise, sunset, dawn, dusk)
        }

        private fun crossings(
            altitudeDeg: Double,
            transit: Double,
            phi: Double,
            declination: Double,
        ): Pair<Instant?, Instant?> {
            val cosHourAngle =
                (sin(altitudeDeg * RAD) - sin(phi) * sin(declination)) /
                    (cos(phi) * cos(declination))
            // The sun never reaches this altitude (polar day/night): no crossing.
            if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return null to null

            val hourAngle = acos(cosHourAngle) / RAD // degrees
            val rise = instantFromJulian(transit - hourAngle / 360.0)
            val set = instantFromJulian(transit + hourAngle / 360.0)
            return rise to set
        }

        private fun instantFromJulian(jd: Double): Instant =
            Instant.ofEpochSecond(((jd - 2_440_587.5) * 86_400.0).roundToLong())
    }
}
