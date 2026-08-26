package com.mab.aura.core.model

import com.mab.aura.core.wind.WindDirection
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One surface observation from AEMET's conventional network (`/observacion/convencional/todas`), which
 * returns many recent records per station across the country. Aura uses it to show a real *observed*
 * temperature near a location instead of the forecast value for the current hour.
 *
 * Direct port of `StationObservation.swift`. Swift's `Decodable` becomes kotlinx's [@Serializable]; this
 * is a wire type only (never persisted). Every field but [idema] is nullable with a `= null` default so
 * one malformed record can't abort the whole decode, and so kotlinx tolerates a missing key the way
 * Swift's `Codable` decodes a missing optional as nil (the client's `Json` also sets `ignoreUnknownKeys`).
 */
@Serializable
data class StationObservation(
    /** Station identifier (AEMET "idema"). */
    val idema: String,
    /** Human-readable station location, e.g. "MADRID RETIRO". */
    val ubi: String? = null,
    /** Station latitude / longitude (decimal degrees). */
    val lat: Double? = null,
    val lon: Double? = null,
    /** Air temperature, °C. */
    val ta: Double? = null,
    /** Relative humidity, %. */
    val hr: Double? = null,
    /** Wind speed, m/s (AEMET reports station wind in m/s; the app shows km/h). */
    val vv: Double? = null,
    /** Wind direction, degrees (0 = N, 90 = E). */
    val dv: Double? = null,
    /** Barometric pressure, hPa. */
    val pres: Double? = null,
    /** Precipitation over the station's accumulation period, mm. */
    val prec: Double? = null,
    /** Reading timestamp, e.g. "2026-08-19T15:00:00+0000". */
    val fint: String? = null,
) {
    /**
     * Air temperature rounded to a whole degree, for display. Swift's `.rounded()` rounds ties away from
     * zero; `ta` genuinely goes negative in Spain, so round the magnitude and reapply the sign. A plain
     * `roundToInt` ties toward +∞ and would disagree at, say, -0.5 (Swift -1 vs roundToInt 0).
     */
    val temperature: Int?
        get() = ta?.let { val mag = Math.round(abs(it)).toInt(); if (it < 0) -mag else mag }

    /** The reading time, parsed from [fint]; null when absent or malformed. */
    val timestamp: Instant?
        get() = fint?.let {
            try {
                OffsetDateTime.parse(it, FORMATTER).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }

    /** Station name in title case ("Madrid Retiro"), from AEMET's all-caps [ubi]. */
    val stationName: String?
        // Swift's `.capitalized` lowercases each word then uppercases its first letter; mirror both
        // halves per word (as WeatherAlert's phenomenon fallback does), with the Spanish locale.
        get() = ubi?.split(" ")?.joinToString(" ") { word ->
            word.lowercase(SPAIN).replaceFirstChar { it.uppercase() }
        }

    /**
     * The station's actual surface values, in display units, for the observation card. Each field is null
     * when the station doesn't report that metric. Wind is converted from AEMET's m/s to km/h (×3.6) so it
     * matches every other wind reading in the app; direction snaps to the 16-point compass.
     */
    val reading: ObservedReading
        get() = ObservedReading(
            temperature = temperature,
            humidity = hr?.let { it.roundToInt() },
            windKmh = vv?.let { (it * 3.6).roundToInt() },
            windDirection = dv?.let { WindDirection.fromDegrees(it) },
            pressure = pres?.let { it.roundToInt() },
            precipMm = prec,
        )

    /**
     * Which surface metrics this reading actually carries, so the card can show whether the resolving
     * station reports everything or only some fields. Gated on the raw wire field being present (wind on
     * [vv], not [dv]) — a station can report a direction with no speed and still not "measure wind".
     */
    val availableMetrics: ObservedMetrics
        get() {
            var flags = 0
            if (ta != null) flags = flags or ObservedMetrics.TEMPERATURE
            if (vv != null) flags = flags or ObservedMetrics.WIND
            if (hr != null) flags = flags or ObservedMetrics.HUMIDITY
            if (pres != null) flags = flags or ObservedMetrics.PRESSURE
            if (prec != null) flags = flags or ObservedMetrics.PRECIPITATION
            return ObservedMetrics(flags)
        }

    /** Great-circle distance from location [to] to this station, km — null when the reading has no coordinates. */
    fun distanceKm(to: Location): Double? {
        val la = lat ?: return null
        val lo = lon ?: return null
        return distanceKm(to.latitude, to.longitude, la, lo)
    }

    companion object {
        private val SPAIN: Locale = Locale.forLanguageTag("es-ES")

        // AEMET stamps observations like "2026-08-19T15:00:00+0000"; the `Z` letter parses the "+0000".
        private val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)

        /**
         * The freshest, nearest station reading to a coordinate, or null when none is close and recent
         * enough. [observations] is the raw list (many records per station): keep each station's latest
         * reading with a temperature, drop readings older than [maxAge], and return the nearest within
         * [maxDistanceKm] (beyond which a reading isn't representative of the location).
         */
        fun nearest(
            latitude: Double,
            longitude: Double,
            observations: List<StationObservation>,
            now: Instant = Instant.now(),
            maxAge: Duration = Duration.ofHours(3),
            maxDistanceKm: Double = 35.0,
        ): StationObservation? {
            // Keep only each station's freshest reading that actually carries a temperature and coordinates.
            val latest = HashMap<String, StationObservation>()
            for (obs in observations) {
                if (obs.ta == null || obs.lat == null || obs.lon == null) continue
                val prev = latest[obs.idema]
                if (prev != null &&
                    (prev.timestamp ?: Instant.MIN) >= (obs.timestamp ?: Instant.MIN)
                ) continue
                latest[obs.idema] = obs
            }

            return latest.values
                .filter { obs -> obs.timestamp?.let { Duration.between(it, now).abs() <= maxAge } ?: false }
                .mapNotNull { obs ->
                    // lat/lon are non-null here (filtered above) but the compiler can't carry that across
                    // the collection, so unwrap again — mirrors Swift's compactMap guard.
                    val lat = obs.lat ?: return@mapNotNull null
                    val lon = obs.lon ?: return@mapNotNull null
                    obs to distanceKm(latitude, longitude, lat, lon)
                }
                .filter { it.second <= maxDistanceKm }
                .minByOrNull { it.second }
                ?.first
        }
    }
}

/**
 * Which surface metrics a station actually reports in a reading, so the card can show, next to an observed
 * value, whether the resolving station covers everything or only some fields.
 *
 * Direct port of Swift's `ObservedMetrics` OptionSet. Kotlin has no OptionSet, so this is a small value class
 * over an `Int` bit-field with the five flags as companion constants and a [contains] test — the idiomatic
 * Kotlin equivalent. [@JvmInline] keeps it allocation-free.
 */
@Serializable
@JvmInline
value class ObservedMetrics(val rawValue: Int = 0) {
    /** Whether every metric in [flags] is present in this set. */
    fun contains(flags: Int): Boolean = (rawValue and flags) == flags

    companion object {
        const val TEMPERATURE = 1 shl 0
        const val WIND = 1 shl 1
        const val HUMIDITY = 1 shl 2
        const val PRESSURE = 1 shl 3
        const val PRECIPITATION = 1 shl 4
    }
}

/**
 * The actual surface values from the resolving station, for the observation card. Every field is null when
 * the station doesn't report that metric. Wind is stored in km/h (converted from AEMET's m/s) so it matches
 * every other wind reading in the app. Direct port of Swift's `ObservedReading`.
 */
@Serializable
data class ObservedReading(
    /** Air temperature, °C. */
    val temperature: Int? = null,
    /** Relative humidity, %. */
    val humidity: Int? = null,
    /** Wind speed, km/h. */
    val windKmh: Int? = null,
    /** Wind direction (whence it blows), when the station reports it. */
    val windDirection: WindDirection? = null,
    /** Barometric pressure, hPa. */
    val pressure: Int? = null,
    /** Precipitation over the station's accumulation period, mm. */
    val precipMm: Double? = null,
)

/**
 * The nearest recent station reading to a location, or null when none is close and recent enough.
 * Swift models this as `extension Array where Element == StationObservation`.
 */
fun List<StationObservation>.nearest(to: Location): StationObservation? =
    StationObservation.nearest(latitude = to.latitude, longitude = to.longitude, observations = this)

/** Great-circle distance in kilometres (haversine). */
private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val p = Math.PI / 180
    val dLat = (lat2 - lat1) * p
    val dLon = (lon2 - lon1) * p
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * p) * cos(lat2 * p) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(min(1.0, sqrt(a)))
}
