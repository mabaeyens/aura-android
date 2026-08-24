package com.mab.aura.core.lunar

import java.time.Instant
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Lunar phase from a date, by the mean synodic month. Accurate to ~a day, which is plenty for a night
 * disc that only has to *look* like tonight's moon and for "next new/full" countdowns.
 *
 * Direct port of the `MoonPhaseMath` enum in `MoonPhase.swift` (the math half; the `PhasedMoonDisc`
 * `View` at the bottom of that file is UI and lands with the Compose `AuraSky` layer). Swift models it as
 * a caseless `enum` used purely as a namespace; the Kotlin idiom for that is an [object].
 *
 * `illumination` runs 0 (new) → 1 (full); `waxing` is true while the lit limb grows (lit on the right in
 * the northern hemisphere, which is all Aura targets); `fraction` runs 0…1 around the whole cycle from
 * new moon. Unlike [com.mab.aura.core.lunar.LunarPosition] (the *true* elongation, used by the moon
 * sheet), this is the mean cycle that drives the night-sky disc, so the two share no state.
 */
object MoonPhaseMath {
    /** Days, new moon to new moon. */
    const val SYNODIC_MONTH = 29.530588853

    /** A known new moon: 2000-01-06 18:14 UTC. */
    val referenceNewMoon: Instant = Instant.parse("2000-01-06T18:14:00Z")

    /** Position in the cycle, 0 (new) … 0.5 (full) … 1 (new again). */
    fun fraction(date: Instant): Double {
        val days = (date.toEpochMilli() - referenceNewMoon.toEpochMilli()) / 1000.0 / 86_400.0
        // Kotlin's `%` on Double keeps the sign of the dividend, exactly like Swift's truncatingRemainder,
        // so a date before the reference epoch yields a negative remainder that the wrap below corrects.
        val p = (days % SYNODIC_MONTH) / SYNODIC_MONTH
        return if (p < 0) p + 1 else p
    }

    /** Illuminated fraction of the disc, 0 (new) … 1 (full). */
    fun illumination(fraction: Double): Double = (1 - cos(2 * Math.PI * fraction)) / 2

    /** The lit limb grows (waxing) through the first half of the cycle. */
    fun waxing(fraction: Double): Boolean = fraction < 0.5

    /** The eight principal phases with Spanish labels, evenly around the cycle. */
    val principalPhases: List<Pair<String, Double>> = listOf(
        "Luna nueva" to 0.000,
        "Creciente" to 0.125,
        "Cuarto creciente" to 0.250,
        "Gibosa creciente" to 0.375,
        "Luna llena" to 0.500,
        "Gibosa menguante" to 0.625,
        "Cuarto menguante" to 0.750,
        "Menguante" to 0.875,
    )

    /**
     * The Spanish phase name for a fraction — the nearest of the eight principal phases, each owning a
     * 1/8 window centred on its fraction (so "Luna nueva" spans 0.9375…0.0625 across the wrap).
     */
    fun phaseName(fraction: Double): String {
        val idx = (fraction * 8).roundToInt() % 8
        return principalPhases[idx].first
    }

    /**
     * The Spanish phase name from a true illuminated fraction and waxing flag — the path the moon sheet
     * uses so the name agrees with the real "% iluminada" it shows (the fraction is reconstructed from the
     * illumination, then named by the same eight-bucket rule).
     */
    fun phaseName(illumination: Double, waxing: Boolean): String {
        val half = acos(1 - 2 * illumination.coerceIn(0.0, 1.0)) / (2 * Math.PI) // 0 (new) … 0.5 (full)
        return phaseName(if (waxing) half else 1 - half)
    }

    /** The next new moon at or after [date] (the mean-synodic estimate; ~a day's accuracy). */
    fun nextNewMoon(date: Instant): Instant {
        val p = fraction(date)
        // Fraction still to run before the cycle returns to new (p = 1 ≡ 0). At exactly new, jump a whole
        // cycle so "next" is always in the future.
        val remaining = if (p <= 0) 1.0 else 1 - p
        return date.plus(remaining)
    }

    /** The next full moon at or after [date] (full is fraction 0.5). */
    fun nextFullMoon(date: Instant): Instant {
        val p = fraction(date)
        val remaining = if (p < 0.5) 0.5 - p else 1.5 - p
        return date.plus(remaining)
    }

    /** Advance an instant by [cycles] fractions of a synodic month, rounding to the millisecond. */
    private fun Instant.plus(cycles: Double): Instant =
        plusMillis(Math.round(cycles * SYNODIC_MONTH * 86_400.0 * 1000.0))
}
