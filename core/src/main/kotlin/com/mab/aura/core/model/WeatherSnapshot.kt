package com.mab.aura.core.model

import com.mab.aura.core.air.AirQuality
import com.mab.aura.core.serialization.InstantEpochMillisSerializer
import com.mab.aura.core.sky.AuraSunPath
import com.mab.aura.core.uv.UVIndex
import com.mab.aura.core.wind.WindDirection
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The small, self-contained bundle of weather one location needs to render. The app (the fetch hub)
 * writes these to a shared cache; every widget reads from that cache and never calls AEMET itself.
 *
 * Direct port of the `WeatherSnapshot` struct in `WeatherSnapshot.swift` — the logic half only. Swift
 * splits the pure computed helpers into an `extension`; Kotlin keeps them as members here. The `make()`
 * factory and its private AEMET-mapping helpers are the net layer (they map `Location`/`MunicipioForecast`/
 * `MunicipioHourly`) and are not ported yet.
 *
 * Swift's `Date` becomes [java.time.Instant]. This is a persisted (`Codable`) type, so the three stored
 * instants ([sunrise], [sunset], [updated]) serialize via [InstantEpochMillisSerializer]. Every nullable
 * field carries a `= null` default: Swift `Codable` decodes a missing optional key as nil, and kotlinx
 * needs the default to tolerate the same missing key from an older cached snapshot (see the model decode
 * tests). [updated] is the one required non-null instant, so it has no default and is passed by name.
 */
@Serializable
data class WeatherSnapshot(
    /** INE municipality code this snapshot describes. */
    val ine: String,
    /** Display name of the municipality, e.g. "Madrid". */
    val localidad: String,
    /** Province name, e.g. "Madrid". */
    val provincia: String,
    /** Today's forecast low, °C. */
    val tempMin: Int? = null,
    /** Today's forecast high, °C. */
    val tempMax: Int? = null,
    /** Today's forecast peak relative humidity, %. */
    val humedadMax: Int? = null,
    /** The forecast temperature for the current hour, °C. */
    val currentTemp: Int? = null,
    /** A real observed temperature from the nearest recent station, when one is close enough, °C. */
    val observedTemp: Int? = null,
    /** Name of the station [observedTemp] came from, e.g. "Madrid Retiro". */
    val observedStation: String? = null,
    /** How far the resolving station sits from the location, km; null when no station resolved. */
    val observedStationDistanceKm: Double? = null,
    /** Which surface metrics the resolving station actually reports (for the station card's chips). */
    val observedMetrics: ObservedMetrics = ObservedMetrics(),
    /** The resolving station's full surface reading (temp/wind/humidity/pressure/rain), for the station card. */
    val observedReading: ObservedReading? = null,
    /**
     * When [observedTemp] was measured (AEMET `fint`, stamped at the top of the hour in UTC). Null when no
     * station resolved, or when read from a cache written before this field existed. Drives the display-time
     * "most recent" rule ([observedLeadsHero]): the observation leads the hero only when it is at least as
     * recent as the forecast hour. Note it is a different clock from [updated] (when the app last refreshed)
     * and from an observation RSS publish time (~30 min after the hour); never compare those against each other.
     */
    @Serializable(with = InstantEpochMillisSerializer::class)
    val observedAt: Instant? = null,
    /** AEMET sky-state code for the current hour (e.g. "11", "13n"), for the condition icon. */
    val currentSky: String? = null,
    /** AEMET's Spanish description of the current sky state (e.g. "Despejado"). */
    val currentSkyText: String? = null,
    /** Relative humidity for the current hour, %. */
    val currentHumidity: Int? = null,
    /** Precipitation probability for the current hour, %. */
    val currentPrecipProb: Int? = null,
    /** Precipitation amount for the current hour, mm. 0 means dry; null means the feed didn't carry it. */
    val currentPrecipMm: Double? = null,
    /** Snow amount for the current hour, mm. Same rules as [currentPrecipMm]. */
    val currentSnowMm: Double? = null,
    /** Feels-like temperature for the current hour, °C. */
    val currentFeelsLike: Int? = null,
    /** Storm probability for the current hour, %. */
    val currentStormProb: Int? = null,
    /** Current-hour wind speed, km/h. */
    val windSpeed: Int? = null,
    /** Current-hour wind direction (whence it blows), or null when calm/unknown. */
    val windDirection: WindDirection? = null,
    /** Current-hour peak wind gust (racha máxima), km/h. Null when AEMET omits it. */
    val windGust: Int? = null,
    /** Nearest-station air quality, when available. */
    val airQuality: AirQuality? = null,
    /** AEMET's daily-max UV index for today. */
    val uvIndex: UVIndex? = null,
    /** CAMS hourly UV forecast (via Open-Meteo), today + tomorrow. Null/empty when unavailable. */
    val uvHourly: List<UVHourSlot>? = null,
    /** Sunrise, computed on-device for the location. */
    @Serializable(with = InstantEpochMillisSerializer::class)
    val sunrise: Instant? = null,
    /** Sunset, computed on-device for the location. */
    @Serializable(with = InstantEpochMillisSerializer::class)
    val sunset: Instant? = null,
    /** Location latitude, so night-spanning cards can compute the adjacent day's sun times. */
    val latitude: Double? = null,
    /** Location longitude. */
    val longitude: Double? = null,
    /** The next few days' min/max, for the multi-day list. */
    val days: List<DaySnapshot> = emptyList(),
    /** The next few hours, for the hourly strip. */
    val hours: List<HourSlot> = emptyList(),
    /** The most severe active AEMET warning for this location's province, if any. */
    val alert: WeatherAlert? = null,
    /** The community narrative bulletin covering today, when fetched. */
    val bulletin: String? = null,
    /** The bulletin's significant-phenomenon headline, if any. */
    val bulletinPhenomenon: String? = null,
    /** When the app last refreshed this snapshot. */
    @Serializable(with = InstantEpochMillisSerializer::class)
    val updated: Instant,
) {
    /**
     * The card's "now" hero temperature, resolved at DISPLAY time. It shows whichever of the station
     * observation and the current-hour forecast is the more recent reading, so it is never a scalar frozen
     * when the snapshot was built.
     *
     * "Most recent" compares the observation's measurement time ([observedAt], AEMET `fint`) against the instant
     * the hero's forecast hour begins, and a tie goes to the observation — a real measurement of the very hour
     * the forecast predicted. So the observation leads once this hour's reading has published (AEMET publishes
     * ~30 min after the hour); before that the re-anchored forecast, already "for now", leads. A stale
     * observation can never win, because the forecast hour keeps advancing past it — that is what makes this
     * self-cleaning across a day change, with no fixed freshness window.
     *
     * Source priority, first that applies wins:
     *  1. [observedTemp], when [observedLeadsHero] — the observation carries a temperature, is not in the
     *     future, and is at least as recent as the forecast hour (or there is no forecast value to compare
     *     against, so the real reading is the only candidate).
     *  2. The first upcoming strip hour that carries a temperature — [upcomingHours] re-anchors the strip to
     *     [now] (dropping past hours, reconstructing absolute instants for older cached slots), which is why a
     *     day-old cached snapshot still shows *today's* number with no fetch.
     *  3. The stored [currentTemp] — the last good carried reading, never today's max — only when the strip
     *     yields nothing (a thin carry-forward with an empty strip, or a cache so old the strip is exhausted).
     *  4. null, the honest "—", only on a genuine cold start with none of the above.
     *
     * Deliberately *not* today's high: a missing hourly feed must not read as a "now" temperature pinned to the
     * day's peak. This must be a function, not a stored value — a value resolved once at build time is what
     * blanked the hero to "--" at the 2026-08-28 day change. Pass the location's [zone]; it defaults to
     * Europe/Madrid to match [upcomingHours]. Kept aligned with the iOS hero-temperature rule.
     */
    fun heroTemp(now: Instant = Instant.now(), zone: ZoneId = ZoneId.of("Europe/Madrid")): Int? =
        heroTempFrom(upcomingHours(now, zone), now, zone)

    /**
     * Whether the station observation leads the hero at [now]: it carries a temperature, its measurement time
     * [observedAt] is known and not in the future, and it is at least as recent as the current forecast hour
     * (ties — the observation for the hour the forecast predicts — go to the measurement). Equivalent to "the
     * observation is the more recent of the two readings", or the only reading when there is no forecast value.
     * The station's distance was already gated when the snapshot was built (the factory keeps only the nearest
     * recent station within 20 km). Kept identical to the iOS rule.
     */
    fun observedLeadsHero(now: Instant = Instant.now(), zone: ZoneId = ZoneId.of("Europe/Madrid")): Boolean {
        val forecast = upcomingHours(now, zone).firstOrNull { it.temp != null }
        return observationLeads(now, forecastHourStart(forecast, now, zone), forecast?.temp)
    }

    /**
     * Whether the station observation is recent enough to show its card at all: [observedAt] is known, not in
     * the future, and no older than [StationObservation.OBSERVATION_MAX_AGE] (3 h). This is the card's own
     * age gate, separate from [observedLeadsHero] (which decides whether the observation leads the *hero*
     * temperature): a reading can be too old to lead the hero yet still fresh enough to show on its card, or
     * too old for both. Pure time math, so unlike the other display helpers it takes no [zone]. The check runs
     * against the live [now] at display time, so a reading carried forward with no new fetch self-expires the
     * moment it crosses the 3 h line. No [ZoneId] is needed. Kept identical to the iOS `observationIsFresh`.
     */
    fun observationIsFresh(now: Instant = Instant.now()): Boolean {
        val at = observedAt ?: return false
        // A future measurement time (clock skew) is never fresh — mirrors iOS's `fint <= now` guard, which
        // also makes the age below non-negative, so no `.abs()` is needed here (nearest() uses abs because it
        // does not reject the future separately).
        if (at.isAfter(now)) return false
        return Duration.between(at, now) <= StationObservation.OBSERVATION_MAX_AGE
    }

    /**
     * The "when measured" time for the station card, formatted as a locale-neutral 24-hour "HH:MM" (e.g.
     * "14:00"), or null when the reading is from the current clock hour (a current-hour reading is "now" and
     * needs no stamp). A reading from an earlier hour — including the same hour on a different day — returns a
     * time, so a carried-forward reading always shows its real age. Formats [observedAt] in [zone] (civil
     * time; Europe/Madrid by default).
     *
     * This returns only the time; the surrounding "a las %s" / "at %s" label is chrome and is localized in
     * :app (`R.string.card_station_measured_at`), so an English device reads "at 14:00" and a Spanish one
     * "a las 14:00". The time digits are the same in either language. iOS mirrors this split: its
     * `observationDisplayTime` returns the raw time and the view supplies the localized prefix. Same rule on
     * both platforms: a time is returned iff the reading is not from the current clock hour.
     */
    fun observationDisplayTime(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.of("Europe/Madrid"),
    ): String? {
        val at = observedAt ?: return null
        // Same absolute clock hour → no stamp. Madrid's offset from UTC is a whole number of hours, so
        // truncating each instant to the hour lands on the same civil-hour boundary the user sees.
        if (at.truncatedTo(ChronoUnit.HOURS) == now.truncatedTo(ChronoUnit.HOURS)) return null
        return STAMP_FORMAT.format(at.atZone(zone))
    }

    /** The hero temperature from an already re-anchored [strip], so [resolved] and [heroTemp] share one rule. */
    private fun heroTempFrom(strip: List<HourSlot>, now: Instant, zone: ZoneId): Int? {
        val forecast = strip.firstOrNull { it.temp != null }
        return when {
            observationLeads(now, forecastHourStart(forecast, now, zone), forecast?.temp) -> observedTemp
            forecast?.temp != null -> forecast.temp
            else -> currentTemp
        }
    }

    /**
     * The instant the hero's forecast hour begins: the slot's stamped [HourSlot.date], else the current hour
     * start in [zone] (for reconstructed slots without a stamped date, and when there is no forecast slot).
     */
    private fun forecastHourStart(forecast: HourSlot?, now: Instant, zone: ZoneId): Instant =
        forecast?.date ?: now.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant()

    /**
     * Whether the observation is the more recent reading (tie → the measurement), or the only reading when
     * there is no [forecastTemp]. A future (clock-skewed) or absent observation never leads.
     */
    private fun observationLeads(now: Instant, forecastAt: Instant, forecastTemp: Int?): Boolean {
        val at = observedAt ?: return false
        if (observedTemp == null) return false
        if (at.isAfter(now)) return false
        return forecastTemp == null || !at.isBefore(forecastAt)
    }

    /**
     * The whole current-conditions family resolved at DISPLAY time, the generalisation of [heroTemp] to every
     * `current*` field. Returns a copy in which each field is re-derived from the strip re-anchored to [now]
     * ([upcomingHours]) instead of trusting the scalar frozen when the snapshot was built. A day-old cache
     * therefore shows *today's* sky, humidity, wind, feels-like and precip on the hero and every card with no
     * fetch, and the hero can never disagree with the hourly strip's first column: both now read the same
     * re-anchored strip. Every surface calls this once at its display boundary and then reads ordinary
     * properties; no card re-implements now-resolution.
     *
     * Resolution rule — per-field first-non-null, each field independently: for each field, the value from the
     * first upcoming hour that carries it. Temperature, sky and humidity can first appear at different hours
     * (AEMET's rolling tail can lead with a sky-only hour), so a single "first column" would blank whatever
     * that hour omits; taking each field from the first hour that has it never blanks and matches what
     * [heroTemp] already does for temperature alone. Fallback order per field: (1) first upcoming strip hour
     * carrying it; (2) the existing frozen scalar — itself the last good carried reading — so the result is
     * never worse than today's snapshot (a thin carry-forward, or a cache written before the strip carried
     * that field, still shows its carried scalar); the frozen scalar is only overwritten by a real strip
     * value, never blanked by the strip's absence.
     *
     * Temperature is the one field with a measured source: it shows whichever of the station observation and
     * the forecast hour is more recent ([observedLeadsHero]), exactly as [heroTemp] does — computed from this
     * same re-anchored strip, so the hero and the resolved snapshot's `currentTemp` are always the same number.
     * Every other field is pure strip-first-non-null. Kept aligned with iOS `resolved`.
     *
     * An empty strip (a genuine cold start, or a thin snapshot with no carried hours) returns the snapshot
     * unchanged — there is nothing to re-derive from, and the frozen scalars are already the best available —
     * unless a leading observation applies, which still sets the temperature even with no strip.
     *
     * This must stay aligned with the iOS `resolved(at:)`: same field set, same per-field first-non-null rule,
     * same fallback to the frozen scalar. If the platforms ever move to strip-first-column instead, change
     * both together.
     */
    fun resolved(now: Instant = Instant.now(), zone: ZoneId = ZoneId.of("Europe/Madrid")): WeatherSnapshot {
        val strip = upcomingHours(now, zone)
        // Temperature shows whichever of the observation and the forecast is more recent (see [heroTemp]);
        // every other field re-derives from the strip. Compute it from this same strip so the hero and the
        // resolved snapshot's currentTemp can never disagree.
        val heroT = heroTempFrom(strip, now, zone)
        // When the strip is empty there is nothing to re-derive, but a leading observation must still apply —
        // so only return unchanged when the hero temperature is just the frozen scalar.
        if (strip.isEmpty()) return if (heroT != currentTemp) copy(currentTemp = heroT) else this
        // First upcoming hour carrying each field, else keep the frozen scalar. `firstNotNullOfOrNull` walks
        // the strip in order and stops at the first hour whose selector is non-null — per-field, independently.
        fun <T> first(select: (HourSlot) -> T?): T? = strip.firstNotNullOfOrNull(select)
        return copy(
            currentTemp = heroT,
            currentSky = first { it.sky } ?: currentSky,
            currentSkyText = first { it.skyText } ?: currentSkyText,
            currentHumidity = first { it.humidity } ?: currentHumidity,
            currentPrecipProb = first { it.precipProb } ?: currentPrecipProb,
            currentPrecipMm = first { it.precipMm } ?: currentPrecipMm,
            currentSnowMm = first { it.snowMm } ?: currentSnowMm,
            currentFeelsLike = first { it.feelsLike } ?: currentFeelsLike,
            currentStormProb = first { it.stormProb } ?: currentStormProb,
            windSpeed = first { it.windSpeed } ?: windSpeed,
            windDirection = first { it.windDirection } ?: windDirection,
            windGust = first { it.windGust } ?: windGust,
        )
    }

    /** Whether the hero is a real station observation. Now always false — kept for API compatibility. */
    val heroIsObserved: Boolean get() = false

    /**
     * True when the snapshot carries current-hour data from the hourly feed. When the hourly fetch comes
     * back empty these all go null together, leaving a "thin" snapshot whose hero and wind rose would render
     * blank; sync uses this to refuse overwriting a good cached snapshot with a thin one.
     */
    val hasCurrentHourData: Boolean
        get() = currentTemp != null || currentSky != null || currentHumidity != null ||
            currentPrecipProb != null || windSpeed != null || windDirection != null

    /**
     * The stored aviso, but only while it is still active at [now]. A cached snapshot outlives its aviso's
     * window (a favourite is not refetched for an hour), so every surface must gate on this rather than
     * trust the raw [alert], otherwise a widget keeps flashing an aviso the app has already dropped.
     */
    fun activeAlert(now: Instant = Instant.now()): WeatherAlert? = alert?.takeIf { it.isActive(now) }

    /**
     * The hourly strip re-anchored to [now]: hours already past are dropped so the strip always begins at
     * the current hour, even when the snapshot was built earlier or served from cache a day later. The
     * current hour itself is kept as the first column.
     *
     * Each slot's absolute instant is its stamped [HourSlot.date]; for snapshots cached before slots carried
     * one, it's reconstructed by walking the wrapping hour sequence forward from [updated] (the build time),
     * so the fix applies to an already-cached snapshot without waiting for a fresh fetch. If the snapshot is
     * so old nothing remains ahead of [now], the stored strip is returned unchanged.
     *
     * Swift uses a `Calendar` fixed to Europe/Madrid; the java.time equivalent is an injectable [ZoneId].
     */
    fun upcomingHours(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.of("Europe/Madrid"),
    ): List<HourSlot> {
        if (hours.isEmpty()) return hours
        val hourStart = now.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant()

        // Reconstruction anchor for null-date slots: the build day's midnight, advanced one day each time
        // the hour sequence wraps past midnight. The strip starts at/after the build hour, so the first slot
        // belongs to the build day. `anchorDay`/`prevHour` are only touched for slots without a stamped date,
        // mirroring the Swift early-return, so filter's in-order evaluation reproduces the map's side effects.
        var anchorDay = updated.atZone(zone).toLocalDate().atStartOfDay(zone)
        var prevHour = -1
        val kept = hours.filter { slot ->
            val date = slot.date ?: run {
                if (slot.hour < prevHour) anchorDay = anchorDay.plusDays(1)
                prevHour = slot.hour
                anchorDay.withHour(slot.hour).toInstant()
            }
            !date.isBefore(hourStart)
        }
        return kept.ifEmpty { hours }
    }

    /** The next sun event to happen, for the sunrise/sunset complication. */
    sealed interface SunEvent {
        data class Sunrise(val date: Instant) : SunEvent
        data class Sunset(val date: Instant) : SunEvent
    }

    /**
     * Sunrise if it's still to come today, otherwise sunset if that's still to come, otherwise the next
     * sunrise (sun times barely move day to day, so today's sunrise stands in for tomorrow's after dark).
     */
    fun nextSunEvent(now: Instant = Instant.now()): SunEvent? {
        sunrise?.let { if (now.isBefore(it)) return SunEvent.Sunrise(it) }
        sunset?.let { if (now.isBefore(it)) return SunEvent.Sunset(it) }
        sunrise?.let { return SunEvent.Sunrise(it) }
        return sunset?.let { SunEvent.Sunset(it) }
    }

    /**
     * Whether it's night at [date] for this location — before today's sunrise or after today's sunset. Lets
     * a view pick the moon icon at render time instead of trusting a possibly-stale AEMET day/night code.
     * Falls back to the cached sky code's "n" suffix when sun times are unknown. The stored sun times are
     * re-dated onto [date]'s day first (see [AuraSunPath.onSameDay]): a day-old snapshot's absolute sunset
     * is "before now" at any hour, which would otherwise read as night at noon.
     */
    fun isNight(date: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val sr0 = sunrise
        val ss0 = sunset
        if (sr0 != null && ss0 != null) {
            val sr = AuraSunPath.onSameDay(date, sr0, zone)
            val ss = AuraSunPath.onSameDay(date, ss0, zone)
            return date.isBefore(sr) || !date.isBefore(ss)
        }
        return (currentSky ?: "").endsWith("n")
    }

    /**
     * Anchor for the `make()` factory and its AEMET-mapping helpers, which live as companion extensions in
     * `WeatherSnapshotFactory.kt` (Swift keeps them as a `static` `extension` on the same type). Kept empty
     * here so those `WeatherSnapshot.Companion.xxx` extensions have a companion to attach to.
     */
    companion object {
        /** 24-hour clock for the station card's "a las HH:mm" measured-at stamp (see [observationDisplayTime]). */
        private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    }
}

/**
 * One hour of the hourly strip: the hour of day, its forecast temperature, sky code, and the precipitation
 * probability for the block it falls in. Ported from `HourSlot` in `WeatherSnapshot.swift`.
 */
@Serializable
data class HourSlot(
    /** 0–23, local. */
    val hour: Int,
    val temp: Int? = null,
    /** AEMET estadoCielo code. */
    val sky: String? = null,
    /** Precipitation probability, %. */
    val precipProb: Int? = null,
    /** km/h, for the hourly card's wind row. */
    val windSpeed: Int? = null,
    /** km/h, peak gust for the hour; null when not reported. */
    val windGust: Int? = null,
    // The rest of the current-conditions family, per hour, so [WeatherSnapshot.resolved] can re-derive every
    // current* field at display time from the re-anchored strip — not just temperature. Each is nullable with
    // a `= null` default so a `snapshots.json` written before these existed still decodes, and the resolver
    // falls back to the snapshot's frozen scalar for whatever an older strip doesn't carry.
    /** AEMET's Spanish description of the sky state (e.g. "Despejado"). */
    val skyText: String? = null,
    /** Relative humidity, %. */
    val humidity: Int? = null,
    /** Feels-like temperature, °C. */
    val feelsLike: Int? = null,
    /** Storm probability, %. */
    val stormProb: Int? = null,
    /** Rain amount, mm. 0 means dry; null means the feed didn't carry it. */
    val precipMm: Double? = null,
    /** Snow amount, mm. Same rules as [precipMm]. */
    val snowMm: Double? = null,
    /** Wind direction (whence it blows), or null when calm/unknown. */
    val windDirection: WindDirection? = null,
    /**
     * The absolute instant this hour begins, so the strip can be re-anchored to the real current hour at
     * display time. Null for snapshots cached before this field existed (then [WeatherSnapshot.upcomingHours]
     * reconstructs it).
     */
    @Serializable(with = InstantEpochMillisSerializer::class)
    val date: Instant? = null,
) {
    /** Swift's `Identifiable` id. */
    val id: Int get() = hour
}

/**
 * One day of the multi-day forecast, as a widget or the "Hoy" list needs it. Ported from `DaySnapshot` in
 * `WeatherSnapshot.swift`.
 */
@Serializable
data class DaySnapshot(
    @Serializable(with = InstantEpochMillisSerializer::class)
    val date: Instant,
    val min: Int? = null,
    val max: Int? = null,
    /** Peak relative humidity for the day, %. */
    val humidityMax: Int? = null,
    /** Representative sky code for the day (daytime block), for the condition icon. */
    val sky: String? = null,
    /** Representative wind speed for the day, km/h. */
    val windSpeed: Int? = null,
    /** Representative precipitation probability for the day, % (max across AEMET's coarse blocks). */
    val probPrecip: Int? = null,
) {
    /** Swift's `Identifiable` id. */
    val id: Instant get() = date
}
