package com.mab.aura.core.sky

import java.time.Instant
import java.time.ZoneId

/**
 * Where Aura's sky light sits, and whether it's night. Pure and testable.
 *
 * Partial port of `AuraSunPath` in `AuraSky.swift`: only the [onSameDay] date helper is ported here,
 * because [com.mab.aura.core.model.isNight] needs it now. The on-screen position and altitude maths (the
 * sun/moon arc) is the Compose rendering layer and lands with the `AuraSky` composable, not in `:core`.
 */
object AuraSunPath {
    /**
     * Re-dates a sun time onto the same calendar day as [now], keeping its clock time. The stored
     * `sunrise`/`sunset` are absolute instants stamped when the snapshot was built; once that snapshot is
     * a day old, comparing a live `now` against them decides day/night by the *wrong day* — "today 11:55"
     * is after "yesterday's 21:00 sunset", which would draw a night sky at noon. Sun times drift only a
     * minute or two a day, so re-dating them to [now] restores a correct time-of-day comparison.
     *
     * Swift takes a `Calendar` (defaulting to `.current`); the java.time equivalent is a [ZoneId], since the
     * hour/minute/second of an [Instant] only exists relative to a zone. Defaults to the system zone to
     * match the Swift default.
     */
    fun onSameDay(now: Instant, time: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant {
        val t = time.atZone(zone)
        return now.atZone(zone)
            .withHour(t.hour).withMinute(t.minute).withSecond(t.second).withNano(0)
            .toInstant()
    }
}
