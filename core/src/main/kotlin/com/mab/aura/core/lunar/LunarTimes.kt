package com.mab.aura.core.lunar

import java.time.Duration
import java.time.Instant
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Degree-based trig helpers, shared by [LunarPosition] and [LunarTimes]. Swift keeps these as `static`
// members on the struct (`Self.sind`, …); the file-private top-level function is the Kotlin equivalent and
// drops the `Self.` noise. `rev` normalises an angle to 0…360.
private const val DEG = Math.PI / 180.0

private fun rev(x: Double): Double {
    val r = x % 360.0
    return if (r < 0) r + 360 else r
}

private fun sind(d: Double): Double = sin(d * DEG)
private fun cosd(d: Double): Double = cos(d * DEG)
private fun atan2d(y: Double, x: Double): Double = atan2(y, x) / DEG
private fun asind(x: Double): Double = asin(x.coerceIn(-1.0, 1.0)) / DEG
private fun acosd(x: Double): Double = acos(x.coerceIn(-1.0, 1.0)) / DEG

/**
 * The Moon's true position, illuminated fraction, and horizon geometry for a location.
 *
 * Direct port of `LunarPosition` in `LunarTimes.swift`. Uses Schlyter's abbreviated lunar theory (the main
 * dozen perturbation terms): good to a few arcminutes in position and a couple of minutes in rise/set time,
 * which is all a "tonight's moon" readout needs. Unlike [MoonPhaseMath] (the mean synodic cycle, which
 * drives the night-sky disc), this carries the *true* elongation from the Sun, so the sheet's "% iluminada"
 * is the real figure and the real horizon crossings give honest moonrise/moonset. Angles are handled in
 * degrees internally.
 *
 * This is a dense mechanical transcription: every constant matters, and one wrong digit is a silent error
 * rather than a crash, so it is parity-checked against `LunarTimesTests.swift` (see [LunarTimesTest]).
 */
class LunarPosition(date: Instant) {
    /** Geocentric apparent right ascension of date, in degrees. */
    val rightAscension: Double
    /** Geocentric apparent declination of date, in degrees. */
    val declination: Double
    /** The Moon's equatorial horizontal parallax, in degrees (drives the rise/set target altitude). */
    val parallax: Double
    /** The Sun's mean longitude at this instant, in degrees — the reference for local sidereal time. */
    val sunMeanLongitude: Double
    /** Illuminated fraction of the disc, 0 (new) … 1 (full), from the true Sun–Moon elongation. */
    val illumination: Double
    /** True while the Moon is east of the Sun (the lit limb growing). */
    val waxing: Boolean

    init {
        // Days since Schlyter's epoch 1999-12-31 00:00 UT (JD 2451543.5), with fractional UT.
        val jd = date.toEpochMilli() / 1000.0 / 86_400.0 + 2_440_587.5
        val d = jd - 2_451_543.5

        // --- Sun: needed for the Moon's perturbations, for the elongation, and for sidereal time. ---
        val ws = 282.9404 + 4.70935e-5 * d          // longitude of perihelion
        val ms = rev(356.0470 + 0.9856002585 * d)   // mean anomaly
        val es = 0.016709 - 1.151e-9 * d
        val ls = rev(ws + ms)                        // Sun's mean longitude
        // Sun's true longitude via its equation of centre.
        val eSun = ms + es * (180.0 / Math.PI) * sind(ms) * (1 + es * cosd(ms))
        val xs = cosd(eSun) - es
        val ys = sqrt(1 - es * es) * sind(eSun)
        val vs = atan2d(ys, xs)
        val lonSun = rev(vs + ws)

        // --- Moon orbital elements. ---
        val n = rev(125.1228 - 0.0529538083 * d)    // longitude of ascending node
        val i = 5.1454
        val w = rev(318.0634 + 0.1643573223 * d)    // argument of perigee
        val a = 60.2666                             // semi-major axis, in Earth radii
        val e = 0.054900
        val m = rev(115.3654 + 13.0649929509 * d)   // mean anomaly

        // Kepler, iterated (the Moon's e is small but not negligible).
        var eAnom = m + e * (180.0 / Math.PI) * sind(m) * (1 + e * cosd(m))
        repeat(3) {
            eAnom -= (eAnom - e * (180.0 / Math.PI) * sind(eAnom) - m) / (1 - e * cosd(eAnom))
        }
        val x = a * (cosd(eAnom) - e)
        val y = a * sqrt(1 - e * e) * sind(eAnom)
        val r0 = sqrt(x * x + y * y)                // distance, Earth radii (pre-perturbation)
        val v = rev(atan2d(y, x))

        // Position in the ecliptic (geocentric), from the orbital plane.
        val xeclip = r0 * (cosd(n) * cosd(v + w) - sind(n) * sind(v + w) * cosd(i))
        val yeclip = r0 * (sind(n) * cosd(v + w) + cosd(n) * sind(v + w) * cosd(i))
        val zeclip = r0 * (sind(v + w) * sind(i))
        var lon = rev(atan2d(yeclip, xeclip))
        var lat = atan2d(zeclip, sqrt(xeclip * xeclip + yeclip * yeclip))

        // --- Perturbations (the terms that matter at arcminute level). ---
        val lm = rev(n + w + m)     // Moon's mean longitude
        val bigD = rev(lm - ls)     // mean elongation
        val f = rev(lm - n)         // argument of latitude

        lon += -1.274 * sind(m - 2 * bigD) +      // evection
            0.658 * sind(2 * bigD) -              // variation
            0.186 * sind(ms) -                    // yearly equation
            0.059 * sind(2 * m - 2 * bigD) -
            0.057 * sind(m - 2 * bigD + ms) +
            0.053 * sind(m + 2 * bigD) +
            0.046 * sind(2 * bigD - ms) +
            0.041 * sind(m - ms) -
            0.035 * sind(bigD) -                  // parallactic equation
            0.031 * sind(m + ms) -
            0.015 * sind(2 * f - 2 * bigD) +
            0.011 * sind(m - 4 * bigD)
        lat += -0.173 * sind(f - 2 * bigD) -
            0.055 * sind(m - f - 2 * bigD) -
            0.046 * sind(m + f - 2 * bigD) +
            0.033 * sind(f + 2 * bigD) +
            0.017 * sind(2 * m + f)
        val r = r0 - 0.58 * cosd(m - 2 * bigD) - 0.46 * cosd(2 * bigD)   // distance, Earth radii
        lon = rev(lon)

        // --- Ecliptic → equatorial, obliquity of date. ---
        val ecl = 23.4393 - 3.563e-7 * d
        val xg = cosd(lon) * cosd(lat)
        val yg = sind(lon) * cosd(lat)
        val zg = sind(lat)
        val xe = xg
        val ye = yg * cosd(ecl) - zg * sind(ecl)
        val ze = yg * sind(ecl) + zg * cosd(ecl)
        rightAscension = rev(atan2d(ye, xe))
        declination = atan2d(ze, sqrt(xe * xe + ye * ye))
        parallax = asind(1 / r)     // horizontal parallax
        sunMeanLongitude = ls

        // --- Illuminated fraction, from the true elongation (Sun on the ecliptic, lat ≈ 0). ---
        val elong = acosd(cosd(lon - lonSun) * cosd(lat))
        illumination = (1 - cosd(elong)) / 2
        // East of the Sun (0…180° of elongation ahead) → waxing.
        waxing = rev(lon - lonSun) < 180
    }

    /**
     * Geocentric altitude of the Moon's centre, in degrees, seen from ([latitude], [longitude]) — used to
     * find the horizon crossings. Longitude is east-positive.
     */
    fun altitude(latitude: Double, longitude: Double, at: Instant): Double {
        val utHours = (at.toEpochMilli() / 1000.0 % 86_400.0) / 3_600.0
        val ut = if (utHours < 0) utHours + 24 else utHours
        val gmst0 = (sunMeanLongitude + 180) / 15   // sidereal time at Greenwich 0h, in hours
        val lst = gmst0 + ut + longitude / 15        // local sidereal time, hours
        val ha = rev(lst * 15 - rightAscension)      // hour angle, degrees
        return asind(
            sind(latitude) * sind(declination) +
                cosd(latitude) * cosd(declination) * cosd(ha),
        )
    }
}

/**
 * Today's moonrise and moonset for a location, from [LunarPosition]. Reports the appearance the Moon is
 * currently in (or the next one): if the Moon is up now, [moonrise] is the crossing that began it and
 * [moonset] the crossing that ends it; if it's down, [moonrise] is the next crossing up and [moonset] the
 * one after. Either can be null on the rare day the Moon doesn't cross (once a month it skips a calendar
 * day).
 *
 * Direct port of the `LunarTimes` struct in `LunarTimes.swift`.
 */
class LunarTimes(now: Instant, latitude: Double, longitude: Double) {
    val moonrise: Instant?
    val moonset: Instant?

    init {
        // The Moon's rise/set target altitude folds in its parallax, semidiameter and refraction (Meeus):
        // h0 = 0.7275·parallax − 34′. Recomputed per sample since the parallax drifts through the month.
        fun target(p: LunarPosition): Double = 0.7275 * p.parallax - 0.5667

        // Scan a window bracketing `now` at a fine step and collect every upward (rise) and downward (set)
        // crossing of (altitude − target), interpolating each crossing linearly.
        val stepSeconds = 5L * 60
        val start = now.minusSeconds(24 * 3_600)
        val count = (54 * 3_600) / stepSeconds       // -24 h … +30 h

        val rises = ArrayList<Instant>()
        val sets = ArrayList<Instant>()
        var prev = start
        var prevPos = LunarPosition(prev)
        var prevDiff = prevPos.altitude(latitude, longitude, prev) - target(prevPos)
        for (k in 1..count) {
            val t = start.plusSeconds(stepSeconds * k)
            val pos = LunarPosition(t)
            val diff = pos.altitude(latitude, longitude, t) - target(pos)
            if (prevDiff < 0 && diff >= 0) {          // crossing up → rise
                rises.add(interp(prev, prevDiff, t, diff))
            } else if (prevDiff >= 0 && diff < 0) {   // crossing down → set
                sets.add(interp(prev, prevDiff, t, diff))
            }
            prev = t
            prevDiff = diff
        }

        // Is the Moon up right now? Pick the coherent rise/set pair around `now`.
        val posNow = LunarPosition(now)
        val up = posNow.altitude(latitude, longitude, now) - target(posNow) >= 0
        if (up) {
            moonrise = rises.lastOrNull { !it.isAfter(now) }
            moonset = sets.firstOrNull { it.isAfter(now) }
        } else {
            val nextRise = rises.firstOrNull { it.isAfter(now) }
            moonrise = nextRise
            moonset = nextRise?.let { r -> sets.firstOrNull { it.isAfter(r) } }
        }
    }

    private companion object {
        /** Linear interpolation to where (altitude − target) hits zero between two samples. */
        fun interp(t0: Instant, d0: Double, t1: Instant, d1: Double): Instant {
            val f = d0 / (d0 - d1)                     // where diff hits zero between the samples
            val spanMillis = Duration.between(t0, t1).toMillis()
            return t0.plusMillis(Math.round(spanMillis * f))
        }
    }
}
