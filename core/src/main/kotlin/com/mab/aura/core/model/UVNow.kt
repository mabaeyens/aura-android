package com.mab.aura.core.model

import com.mab.aura.core.sky.SkyCategory
import com.mab.aura.core.sky.SkyCode
import java.time.Instant
import java.time.ZoneId

/**
 * The scalar UV readouts the "Índice UV" card prints above its hourly strip, computed in `:core` so they
 * can be unit-tested without pulling in Compose. The strip itself (per-hour bar heights, band tints, the
 * "now" outline) stays in the card; these are the derived numbers it captions the strip with.
 *
 * Ported from the private computed properties of `UVHourStrip` in `AuraAppCards.swift` — `peak`,
 * `protectionWindow`, and the "Ahora N" live reading — gathered into one value type. The Swift view read
 * these off its `today` slice inline; here they are one pure function so the (non-trivial) protection-window
 * arithmetic is covered by tests instead of living untested in `:app`.
 *
 * Hour-of-day extraction takes an injectable [ZoneId] (default: the system zone) so a caller can pin it to
 * Europe/Madrid, mirroring how the Swift view used `Calendar.current.component(.hour, from:)`.
 */
data class UVNow(
    /**
     * The live rounded WHO index for the hour containing `now`, or null when there's no covering slot or
     * that hour's UV is 0 (night / pre-dawn) — the card only shows "Ahora N" when this is present.
     */
    val nowIndex: Int?,
    /** Today's peak rounded WHO index, or null when the series is empty. */
    val peakIndex: Int?,
    /** Hour-of-day (in [zone]) of today's peak slot, for "máx N a las Hh". Null when the series is empty. */
    val peakHour: Int?,
    /**
     * The stretch of today where the UV sits at or above the WHO protection threshold, as a half-open hour
     * range `[start, end)` — `end` is the hour *after* the last qualifying hour, so it reads as "protected
     * until". Null when the UV never reaches the threshold today (nothing to advise).
     */
    val protection: IntRange?,
) {
    companion object {
        /** WHO threshold at which protection is advised — the start of the "Moderado" band. */
        const val PROTECTION_THRESHOLD = 3

        /**
         * Derive the readouts from [today] — the caller's today-and-daytime slice
         * (`todaySlots(...).filter { it.uv > 0 }`) — evaluated at [now] in [zone].
         */
        fun from(
            today: List<UVHourSlot>,
            now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): UVNow {
            fun hour(instant: Instant): Int = instant.atZone(zone).hour

            val nowIndex = today.current(now)?.takeIf { it.uv > 0 }?.index
            val peak = today.maxByOrNull { it.uv }
            val above = today.filter { it.index >= PROTECTION_THRESHOLD }
            val protection = if (above.isNotEmpty()) {
                hour(above.first().date)..(hour(above.last().date) + 1)
            } else {
                null
            }
            return UVNow(nowIndex, peak?.index, peak?.let { hour(it.date) }, protection)
        }

        /**
         * True when the current sky is overcast or wet enough that cloud is materially holding the UV index
         * below its clear-sky value. Drives the UV card's cloud cue: it tells the reader the number is the
         * cloudy reading, not the clear-sky potential. Ported from Swift `UVNow.cloudy`.
         */
        fun cloudy(snapshot: WeatherSnapshot): Boolean =
            when (SkyCode.classify(snapshot.currentSky).category) {
                SkyCategory.OVERCAST, SkyCategory.RAIN, SkyCategory.STORM,
                SkyCategory.SNOW, SkyCategory.FOG -> true
                else -> false
            }
    }
}
