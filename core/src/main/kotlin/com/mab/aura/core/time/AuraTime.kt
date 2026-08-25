package com.mab.aura.core.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * One clock-time formatter shared by every surface, so a single 24 h / 12 h preference controls how times
 * read everywhere. Direct port of `AuraTime.swift`.
 *
 * Only wall-clock times (sunrise, sunset, moonrise, the hourly strip, "actualizado a las…") flow through
 * here. Calendar dates ("28 ago") keep their own day/month formatting — the toggle is about the clock.
 *
 * Android note: the Swift original reads the preference straight from the App Group `UserDefaults`, which is
 * synchronous. Android's DataStore is asynchronous (Flow/suspend), and these formatters are called from
 * Compose synchronously (the arc cards, the sheets), so the flag lives here as a plain in-memory value that
 * `:app` keeps in step with the DataStore boolean (collect the flow once, assign [use24h]). `:core` stays
 * free of any `android.*` import; it just holds the last-known preference. The functions also take `use24h`
 * as an explicit argument (defaulting to the shared flag) so the formatting is pure and unit-testable
 * without touching global state.
 */
object AuraTime {

    /**
     * The current clock preference. `true` (the default) is 24-hour; `false` is 12-hour AM/PM. Spain runs
     * on a 24-hour clock, so 24 h is the sensible default before `:app` has read the stored value.
     *
     * `@Volatile` because `:app` writes it from a coroutine collecting the DataStore flow while Compose
     * reads it on the main thread — this guarantees the read sees the latest write.
     */
    @Volatile
    var use24h: Boolean = true

    /** A wall-clock time: "18:34" in 24-hour, "6:34 PM" in 12-hour, per [use24h]. */
    fun hhmm(
        instant: Instant,
        use24h: Boolean = this.use24h,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = (if (use24h) formatter24 else formatter12).withZone(zone).format(instant)

    /**
     * A bare hour for the hourly strip: "18h" in 24-hour, "6 AM"/"6 PM" in 12-hour. Takes the 0…23 hour
     * directly (the strip carries the hour as an integer), so no [Instant]/zone round-trip is needed.
     */
    fun hourLabel(hour: Int, use24h: Boolean = this.use24h): String {
        if (use24h) return "${hour}h"
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "$h12 ${if (hour < 12) "AM" else "PM"}"
    }

    private val esES: Locale = Locale.forLanguageTag("es-ES")

    // "HH:mm" — 24-hour, zero-padded ("06:05", "18:34").
    private val formatter24 = DateTimeFormatterBuilder()
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .toFormatter(esES)

    // "h:mm AM/PM" — 12-hour, no leading zero on the hour. The AM/PM text is forced to the uppercase Latin
    // pair (as the Swift set amSymbol/pmSymbol explicitly), since java.time's es-ES default renders "a. m."
    // / "p. m.", which is not what the design wants.
    private val formatter12 = DateTimeFormatterBuilder()
        .appendValue(ChronoField.CLOCK_HOUR_OF_AMPM)
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .appendLiteral(' ')
        .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to "AM", 1L to "PM"))
        .toFormatter(esES)
}
