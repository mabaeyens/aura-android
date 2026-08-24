package com.mab.aura.core.model

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
