package com.mab.aura.core.model

import com.mab.aura.core.serialization.InstantEpochMillisSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.roundToInt

/**
 * One hour of the CAMS UV forecast (via Open-Meteo): the instant, the forecast UV index — which
 * includes the attenuation from forecast cloud — and the clear-sky UV index for the same hour.
 *
 * Direct port of the `UVHourSlot` struct in `UVHourly.swift`. The `OpenMeteoUV` fetch half of that
 * Swift file is Layer C (the net layer, `core/net/OpenMeteoService`) and is not ported here — this is
 * the pure model plus the list helpers the cards read it through. Swift's `Date` becomes
 * [java.time.Instant]; it's a stored field of the persisted `WeatherSnapshot`, so it serializes via
 * [InstantEpochMillisSerializer]. Swift's `Identifiable` (`id: Date`) has no Kotlin analogue and is
 * dropped; the identity was just the date.
 */
@Serializable
data class UVHourSlot(
    /** The hour's instant (UTC from Open-Meteo; render in the location's zone). */
    @Serializable(with = InstantEpochMillisSerializer::class)
    val date: Instant,
    /** Forecast UV index for the hour, cloud effect included. */
    val uv: Double,
    /** What the UV index would be under a cloudless sky at the same place and hour. */
    val clearSky: Double,
) {
    /** Rounded to the whole WHO 0–11+ index the cards and bands use. */
    val index: Int
        // UV is always >= 0, so roundToInt (ties toward +inf) matches Swift's `.rounded()`
        // (ties away from zero) for every value that can occur here.
        get() = uv.roundToInt()
}

// Swift models these as `extension Array where Element == UVHourSlot`; the Kotlin idiom is extension
// functions on `List<UVHourSlot>`.

/**
 * The slot covering [now] — the hour-long window `[date, date+1h)` that contains it — for a live
 * "UV ahora" reading. Timezone-free by construction: it compares absolute instants, so it's right
 * wherever the viewer is. Null when [now] falls outside the fetched span (e.g. a stale snapshot).
 */
fun List<UVHourSlot>.current(now: Instant = Instant.now()): UVHourSlot? =
    firstOrNull { !it.date.isAfter(now) && now.isBefore(it.date.plusSeconds(3600)) }

/**
 * Today's hourly slots. The feed is fetched `timezone=auto`, so its first 24 hours are already the
 * location's local day starting at 00:00 — this keeps the run up to (and including) tomorrow's
 * midnight boundary, i.e. today. Falls back to the first 24 slots when the run can't be found.
 *
 * The [now] parameter is unused (kept for signature symmetry with the Swift extension and with
 * [todayMax]); the window is anchored on the feed's own first hour, not the device day.
 */
@Suppress("UNUSED_PARAMETER")
fun List<UVHourSlot>.todaySlots(now: Instant = Instant.now()): List<UVHourSlot> {
    val first = firstOrNull() ?: return emptyList()
    // The API aligns hour 0 to the location's local midnight; today is the first 24 of those hours.
    // Anchor on the feed's own start so it's correct regardless of the viewer's zone.
    val end = first.date.plusSeconds(24 * 3600)
    val sameDay = filter { !it.date.isBefore(first.date) && it.date.isBefore(end) }
    return sameDay.ifEmpty { take(24) }
}

/** The peak forecast-UV hour today, for a "máx hoy" figure drawn from the same series. */
fun List<UVHourSlot>.todayMax(now: Instant = Instant.now()): UVHourSlot? =
    todaySlots(now).maxByOrNull { it.uv }
