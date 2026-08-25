package com.mab.aura.core.air

import com.mab.aura.core.serialization.InstantEpochMillisSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Locale
import kotlin.math.floor

/**
 * One measured pollutant at the nearest station: its latest valid hourly concentration in µg/m³. Only
 * pollutants the station actually reports appear — a traffic station that measures only NO₂ contributes
 * a single component, never a fabricated zero for the rest.
 *
 * Direct port of the `AirComponent` struct in `AirQuality.swift` — the logic half only. The
 * `MitecoAirQuality` client (CSV feed, backend POST, nearest-station/haversine resolution) from that
 * Swift file is Layer C (the net layer, `core/net/AirQualityService`) and is not ported here. Swift's
 * `Date?` becomes [java.time.Instant]; `AirComponent` rides inside the persisted `WeatherSnapshot`
 * (via `AirQuality`), so the instant serializes via [InstantEpochMillisSerializer].
 */
@Serializable
data class AirComponent(
    /** Canonical MITECO magnitud token: "NO2", "O3", "PM2.5", "PM10", "SO2". */
    val pollutant: String,
    /**
     * The ICA value in µg/m³ — the running mean the índice uses (8 h for O₃, 24 h for PM), or the last
     * valid hour for NO₂/SO₂ where the ICA uses no average. Not the raw hourly reading for O₃/PM.
     */
    val value: Double,
    /**
     * The station this pollutant was read from, and how far it sits (km). Each pollutant is taken from
     * the *nearest station that measures it*, so different pollutants can come from different stations —
     * hence the source travels with the component. Null for legacy/preview components built without one.
     */
    val station: String? = null,
    val distanceKm: Double? = null,
    /** When this pollutant was measured (the reading's UTC hour). Null when unknown. */
    @Serializable(with = InstantEpochMillisSerializer::class)
    val measured: Instant? = null,
) {
    /** Display order for the breakdown, mirroring the official ICA listing. */
    val rank: Int get() = order.indexOf(pollutant).let { if (it < 0) order.size else it }

    /** Subscripted label, e.g. "NO₂", "O₃", "PM2,5". */
    val label: String get() = label(pollutant)

    /** Indicative ICA band (1…6) for this pollutant's latest hourly value. 0 for an unknown token. */
    val icaCategory: Int
        get() {
            val bands = bands(pollutant) ?: return 0
            bands.forEachIndexed { i, upper -> if (value <= upper) return i + 1 }
            return 6
        }

    /**
     * Where this value sits along the 1…6 scale as a continuous 0…1 fraction (for a scale-bar marker):
     * its band index plus how far it has climbed within that band. Category 6 (above the top breakpoint)
     * is capped near the far end. Returns 0 for a token with no scale.
     */
    val icaFraction: Double
        get() {
            val bands = bands(pollutant) ?: return 0.0
            val c = icaCategory
            val lower = if (c == 1) 0.0 else bands[c - 2]
            val upper = if (c <= 5) bands[c - 1] else bands[4] * 1.5 // open-ended top band: cap the climb
            val within = ((value - lower) / maxOf(upper - lower, 0.0001)).coerceIn(0.0, 1.0)
            return (c - 1 + within) / 6
        }

    /** The value with Spanish decimal comma, e.g. "3,5" or "27". */
    val valueText: String
        get() = if (value == floor(value)) {
            value.toInt().toString()
        } else {
            // Pin to Locale.US so the "%.1f" itself emits a period; only then swap to a comma. On a
            // Spanish-locale device the default format would already produce a comma and the swap
            // would leave a stray one.
            String.format(Locale.US, "%.1f", value).replace('.', ',')
        }

    companion object {
        /**
         * The canonical five ICA pollutants in display order (mirrors the official ICA listing). Public
         * so the air-quality card can render a fixed column per pollutant — greyed for the ones a station
         * doesn't measure — matching the Swift `public static let order`.
         */
        val order = listOf("NO2", "O3", "PM2.5", "PM10", "SO2")

        /**
         * Subscripted label for a bare MITECO magnitud token, so a column can be rendered for a
         * pollutant the station doesn't measure (greyed, no value) as well as for a measured one.
         */
        fun label(pollutant: String): String = when (pollutant) {
            "O3" -> "O₃"
            "NO2" -> "NO₂"
            "SO2" -> "SO₂"
            "PM2.5" -> "PM2,5"
            "PM10" -> "PM10"
            else -> pollutant
        }

        /**
         * The upper µg/m³ bound of ICA categories 1…5 for a pollutant (category 6 is open-ended above
         * the last), from the official Spanish/EEA breakpoints, or null for a token with no scale.
         * Shared by the band and the continuous scale position so they can't disagree.
         */
        fun bands(pollutant: String): List<Double>? = when (pollutant) {
            "NO2" -> listOf(40.0, 90.0, 120.0, 230.0, 340.0)
            "O3" -> listOf(50.0, 100.0, 130.0, 240.0, 380.0)
            "PM10" -> listOf(20.0, 40.0, 50.0, 100.0, 150.0)
            "PM2.5" -> listOf(10.0, 20.0, 25.0, 50.0, 75.0)
            "SO2" -> listOf(100.0, 200.0, 350.0, 500.0, 750.0)
            else -> null
        }
    }
}

/**
 * A nearest-station air-quality reading, from the MITECO national ICA feed. The índice de calidad del
 * aire (ICA) is a 1–6 category, the *worst* of the pollutants a station measures; `pollutant` names the
 * one that drove it.
 *
 * Direct port of the `AirQuality` struct in `AirQuality.swift` (logic half). Stored in the persisted
 * `WeatherSnapshot`, so it and its [AirComponent]s are `@Serializable`; `measured` uses
 * [InstantEpochMillisSerializer].
 */
@Serializable
data class AirQuality(
    /** ICA category 1…6 (1 buena … 6 extremadamente desfavorable). No-data is a nil `AirQuality`. */
    val category: Int,
    /**
     * True when MITECO computed the category from fewer pollutants than the station can measure (the raw
     * índice arrived as category × 10). The category still stands — it's just lower-confidence.
     */
    val partial: Boolean,
    /** Dominant pollutant driving the category ("O3", "NO2", "PM2.5", "PM10", "SO2"), or null. */
    val pollutant: String?,
    /** The station the reading came from, and how far it sits from the location (km). */
    val station: String,
    val distanceKm: Double,
    /** Measurement instant (UTC in the feed). */
    @Serializable(with = InstantEpochMillisSerializer::class)
    val measured: Instant,
    /**
     * Per-pollutant breakdown for the same station (empty when unavailable). Sorted in the canonical
     * [AirComponent] order; only actually-measured pollutants are present.
     */
    val components: List<AirComponent> = emptyList(),
) {
    // Swift sorts `components` inside its `init`. A Kotlin data class can't reorder a constructor arg
    // in place, so the sort lives in the [create]/[adding] factories instead — those are the entry
    // points callers use, and because every instance is built through them the stored list is already
    // in canonical order (and stays that way across a serialize/deserialize round-trip).

    /** A copy with the per-pollutant breakdown attached (headline fields unchanged). */
    fun adding(components: List<AirComponent>): AirQuality =
        create(category, partial, pollutant, station, distanceKm, measured, components)

    /** Spanish ICA category name (the official six-level scale). */
    val categoryName: String get() = categoryName(category)

    /** Dominant pollutant with proper subscripts/formatting, e.g. "O₃", "NO₂", "PM2,5". */
    val pollutantLabel: String?
        get() = when (pollutant) {
            "O3" -> "O₃"
            "NO2" -> "NO₂"
            "SO2" -> "SO₂"
            "PM2.5" -> "PM2,5"
            "PM10" -> "PM10"
            null -> null
            else -> pollutant.ifEmpty { null }
        }

    companion object {
        /**
         * Builds an `AirQuality` with its [components] sorted into the canonical [AirComponent] order,
         * matching the sort the Swift `init` performs. Prefer this over the raw constructor.
         */
        fun create(
            category: Int,
            partial: Boolean,
            pollutant: String?,
            station: String,
            distanceKm: Double,
            measured: Instant,
            components: List<AirComponent> = emptyList(),
        ): AirQuality = AirQuality(
            category, partial, pollutant, station, distanceKm, measured,
            components.sortedBy { it.rank },
        )

        /** The official Spanish ICA name for any 1…6 category, so a per-pollutant band can be named too. */
        fun categoryName(category: Int): String = when (category) {
            1 -> "Buena"
            2 -> "Razonablemente buena"
            3 -> "Regular"
            4 -> "Desfavorable"
            5 -> "Muy desfavorable"
            6 -> "Extremadamente desfavorable"
            else -> "Sin datos"
        }
    }
}
