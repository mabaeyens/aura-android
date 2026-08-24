package com.mab.aura.core.hero

import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.sky.AuraSunPath
import com.mab.aura.core.sky.SkyCategory
import com.mab.aura.core.sky.SkyCode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Picks the hero background *image name* for the current sky and time of day, with a fallback chain that
 * keeps the screen sensible while the 8×6 art grid is still being filled in, and ultimately returns `null`
 * to mean "no art, use the procedural sky".
 *
 * Direct port of the name-resolution half of `HeroBackground.swift`. The resolver is pure and testable:
 * it's handed the *set of asset names that actually exist* and returns the best one. The SwiftUI
 * `Image`-returning helpers (`heroImage`, `wideImage`, `wideBaseImage`) are UI and land with the Compose
 * `AuraSky` layer; here everything resolves to a name (or `null`) that the app turns into a drawable.
 *
 * The art is generated **without any sun or moon**: Aura draws the live sun/moon disc on top at the true
 * solar position, so the only light that moves is the real one. It's the *bytes* behind each name that are
 * the asset-packaging problem (Appendix A of the plan), separate from this logic, which only computes a
 * name.
 */
object HeroBackground {

    // region Axes

    /**
     * The six times of day the grid is cut into, derived from the **real sun path** so they track true
     * sunrise/sunset for the location rather than clock hours alone. [token] is the filename fragment.
     *
     * Declaration order is the daily cycle and matters: the nearest-time fallback measures distance along
     * it (so `DAWN` neighbours `NIGHT`).
     */
    enum class Time(val token: String) {
        DAWN("dawn"),
        MORNING("morning"),
        NOON("noon"),
        AFTERNOON("afternoon"),
        DUSK("dusk"),
        NIGHT("night");

        companion object {
            /**
             * Which bucket [now] falls in, from the sun path. Only the pure part is computed here — the
             * daylight fraction (0 at sunrise → 1 at sunset) and the night flag — mirroring what
             * `AuraSunPath` derives; the on-screen arc geometry stays in the Compose layer.
             *
             * With no sun times (a snapshot built without coordinates) the *label* falls back to the local
             * clock hour rather than pinning to noon, so it still tracks the wall clock. [zone] resolves the
             * clock hour and re-dates the sun times; it defaults to the system zone to match Swift's
             * `Calendar.current`.
             */
            fun from(
                now: Instant,
                sunrise: Instant?,
                sunset: Instant?,
                zone: ZoneId = ZoneId.systemDefault(),
            ): Time {
                if (sunrise == null || sunset == null) return fromClockHour(now.atZone(zone).hour)
                // Re-date the absolute sun times onto now's day: a day-old snapshot must not decide day/night
                // by the wrong day. See AuraSunPath.onSameDay.
                val sr = AuraSunPath.onSameDay(now, sunrise, zone)
                val ss = AuraSunPath.onSameDay(now, sunset, zone)
                if (!ss.isAfter(sr)) return NOON // degenerate span → the neutral noon sky AuraSunPath returns
                if (now.isBefore(sr) || now.isAfter(ss)) return NIGHT
                val f = Duration.between(sr, now).toMillis().toDouble() /
                    Duration.between(sr, ss).toMillis()
                return when {
                    f < 0.12 -> DAWN
                    f < 0.40 -> MORNING
                    f < 0.60 -> NOON
                    f < 0.88 -> AFTERNOON
                    else -> DUSK
                }
            }

            /** Fallback bucket from the local clock hour, used only when sun times are missing. */
            private fun fromClockHour(hour: Int): Time = when (hour) {
                in 6..8 -> DAWN
                in 9..11 -> MORNING
                in 12..14 -> NOON
                in 15..18 -> AFTERNOON
                in 19..20 -> DUSK
                else -> NIGHT
            }
        }
    }

    /**
     * The eight sky conditions with their own art — the [SkyCategory] categories except `UNKNOWN` (which
     * has none and falls through to the procedural sky). [token] is the filename fragment.
     */
    enum class Condition(val token: String) {
        CLEAR("clear"),
        FEW_CLOUDS("few_clouds"),
        CLOUDY("cloudy"),
        OVERCAST("overcast"),
        RAINY("rainy"),
        STORMY("stormy"),
        SNOWY("snowy"),
        FOGGY("foggy");

        companion object {
            /** The art condition for a sky category, or `null` for `UNKNOWN` (→ procedural sky). */
            fun from(sky: SkyCategory): Condition? = when (sky) {
                SkyCategory.CLEAR -> CLEAR
                SkyCategory.FEW_CLOUDS -> FEW_CLOUDS
                SkyCategory.CLOUDS -> CLOUDY
                SkyCategory.OVERCAST -> OVERCAST
                SkyCategory.RAIN -> RAINY
                SkyCategory.STORM -> STORMY
                SkyCategory.SNOW -> SNOWY
                SkyCategory.FOG -> FOGGY
                SkyCategory.UNKNOWN -> null
            }
        }
    }

    /**
     * The art *family* — a whole alternate set of 48 (same 8×6 grid) with different scenery. Landscape
     * keeps the **bare** name so its 48 assets never need renaming; cityscape carries a `city_` prefix on
     * the same flat name.
     */
    enum class Family(val assetPrefix: String, val displayName: String) {
        LANDSCAPE("", "Paisaje"),
        CITYSCAPE("city_", "Ciudad");

        companion object {
            /** Decode the persisted setting string; any unknown value falls back to [LANDSCAPE]. */
            fun from(storage: String?): Family =
                entries.firstOrNull { it.name.equals(storage, ignoreCase = true) } ?: LANDSCAPE
        }
    }

    // endregion

    // region Portrait resolver

    /** Canonical asset name, e.g. `"few_clouds_dawn"` (landscape) or `"city_clear_night"` (cityscape). */
    fun assetName(family: Family, condition: Condition, time: Time): String =
        "${family.assetPrefix}${condition.token}_${time.token}"

    /** Every name one family's full 8×6 grid would contain (48). */
    fun assetNames(family: Family): List<String> =
        Condition.entries.flatMap { c -> Time.entries.map { assetName(family, c, it) } }

    /** Every name across all families (96). The app probes these against its assets to learn which shipped. */
    val allAssetNames: List<String> = Family.entries.flatMap { assetNames(it) }

    /**
     * Resolve the best background for a sky + time **within the chosen family**, given which assets exist.
     *
     * Chain: exact `(family, condition, time)` → nearest existing time for the **same** condition in the
     * **same** family → `null` (procedural). It never borrows another condition's art, and never the other
     * family's — a family with no art for this sky falls to the procedural sky, not to the other family.
     */
    fun resolve(
        sky: SkyCategory,
        time: Time,
        family: Family = Family.LANDSCAPE,
        available: Set<String>,
    ): String? {
        val condition = Condition.from(sky) ?: return null
        return resolveName(condition, time, available) { c, t -> assetName(family, c, t) }
    }

    /** Convenience straight from a snapshot. */
    fun resolve(
        snapshot: WeatherSnapshot,
        now: Instant,
        family: Family = Family.LANDSCAPE,
        available: Set<String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        val category = SkyCode.classify(snapshot.currentSky).category
        val time = Time.from(now, snapshot.sunrise, snapshot.sunset, zone)
        return resolve(category, time, family, available)
    }

    // endregion

    // region Wide grid (iPad / widgets)

    /** The intrinsic aspect ratio (width ÷ height) of the wide base art — all four ship at 1400×1050. */
    const val WIDE_BASE_ASPECT: Double = 1400.0 / 1050.0

    /** The intrinsic aspect ratio (width ÷ height) of the 48 portrait hero images — the 9:19.5 grid. */
    const val HERO_ASPECT: Double = 9.0 / 19.5

    /**
     * The wide, **conditionless** base scene for a family and day/night — one image per family per
     * day/night, four in all (`wide_landscape_day`/`_night`, `wide_city_day`/`_night`).
     */
    fun wideBaseName(family: Family, isNight: Boolean): String {
        val scene = if (family == Family.CITYSCAPE) "city" else "landscape"
        return "wide_${scene}_${if (isNight) "night" else "day"}"
    }

    /**
     * Where the **highest scenery** meets the sky in each wide base, as a fraction of the art's height —
     * the line a low dawn/dusk sun must clear to sit in the calm sky instead of in front of the scene.
     */
    fun wideBaseHorizon(family: Family): Double = if (family == Family.CITYSCAPE) 0.84 else 0.50

    /**
     * Where a low dawn/dusk sun should rest against the **portrait** hero (the 48-asset grid), as a
     * fraction of the art's height — the skyline at the frame edges, not the central peak.
     */
    fun heroHorizon(family: Family): Double = if (family == Family.CITYSCAPE) 0.60 else 0.68

    /**
     * Canonical **wide** asset name, the 4:3 twin of the portrait grid, e.g. `"wide_landscape_clear_dawn"`
     * / `"wide_city_stormy_night"`. The scene token is `landscape`/`city` (NOT the portrait `city_`
     * prefix), matching the four legacy bases.
     */
    fun wideAssetName(family: Family, condition: Condition, time: Time): String {
        val scene = if (family == Family.CITYSCAPE) "city" else "landscape"
        return "wide_${scene}_${condition.token}_${time.token}"
    }

    /** Every name one family's full wide 8×6 grid would contain (48). */
    fun wideAssetNames(family: Family): List<String> =
        Condition.entries.flatMap { c -> Time.entries.map { wideAssetName(family, c, it) } }

    /**
     * Resolve the best **wide** asset name for a snapshot within a family, given a predicate reporting which
     * assets exist — same chain as the portrait [resolve]. `null` → no art for this sky, use the procedural
     * sky.
     */
    fun wideName(
        snapshot: WeatherSnapshot?,
        now: Instant,
        family: Family = Family.LANDSCAPE,
        zone: ZoneId = ZoneId.systemDefault(),
        exists: (String) -> Boolean,
    ): String? {
        if (snapshot == null) return null
        val category = SkyCode.classify(snapshot.currentSky).category
        val condition = Condition.from(category) ?: return null
        val time = Time.from(now, snapshot.sunrise, snapshot.sunset, zone)
        val available = wideAssetNames(family).filter(exists).toSet()
        return resolveName(condition, time, available) { c, t -> wideAssetName(family, c, t) }
    }

    /**
     * The wide base **name** for a snapshot's day/night state, or `null` for a missing snapshot. Day/night
     * is decided the same way [Time.from] decides `NIGHT`: sun times re-dated onto now's day.
     */
    fun wideBaseName(
        snapshot: WeatherSnapshot?,
        now: Instant,
        family: Family = Family.LANDSCAPE,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (snapshot == null) return null
        val isNight = Time.from(now, snapshot.sunrise, snapshot.sunset, zone) == Time.NIGHT
        return wideBaseName(family, isNight)
    }

    // endregion

    /**
     * Nearest-time resolver shared by the portrait and wide grids: exact `(condition, time)` → nearest
     * existing time for the **same** condition over the daily cycle (so `dawn` neighbours `night`, ties to
     * the earlier bucket) → `null`. [name] maps a `(condition, time)` to the family's asset name so the two
     * grids share one algorithm and only differ in how they spell their filenames.
     */
    private fun resolveName(
        condition: Condition,
        time: Time,
        available: Set<String>,
        name: (Condition, Time) -> String,
    ): String? {
        val exact = name(condition, time)
        if (exact in available) return exact
        val order = Time.entries
        val want = order.indexOf(time)
        if (want < 0) return null
        val best = order.indices
            .filter { name(condition, order[it]) in available }
            .minByOrNull { cyclicDistance(it, want, order.size) }
        return best?.let { name(condition, order[it]) }
    }

    private fun cyclicDistance(a: Int, b: Int, n: Int): Int {
        val d = abs(a - b)
        return minOf(d, n - d)
    }
}
