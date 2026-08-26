package com.mab.aura.core.model

import com.mab.aura.core.air.AirQuality
import com.mab.aura.core.serialization.InstantEpochMillisSerializer
import com.mab.aura.core.sky.AuraSunPath
import com.mab.aura.core.uv.UVIndex
import com.mab.aura.core.wind.WindDirection
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
     * The card's "now" hero temperature: the current-hour forecast, falling back to today's high only
     * when the hourly feed is missing. Deliberately *not* the observed-station reading — a warm nearby
     * station can read the day's max hours before the forecast says it will, pinning the gauge to the high.
     */
    val heroTemp: Int? get() = currentTemp ?: tempMax

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
    companion object
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
