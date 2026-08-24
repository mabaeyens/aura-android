package com.mab.aura.core.model

import com.mab.aura.core.serialization.InstantEpochMillisSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One AEMET "aviso" (meteorological warning) for a warning zone, parsed from the CAP-XML product.
 * Warnings are matched to a location by province: the 6-digit zone code is
 * `[CCAA area][province INE][zone]`, so `provinceCode` is digits 3–4.
 *
 * Direct port of the `WeatherAlert` struct in `WeatherAlert.swift` — the logic half only. The
 * `CAPParser` (CAP-XML parsing) from that Swift file is Layer C (the net/parse layer) and is not
 * ported here. Swift's `Date?` fields become [java.time.Instant]; this is a stored field of the
 * persisted `WeatherSnapshot`, so the instants serialize via [InstantEpochMillisSerializer]. The
 * Swift `Level.rank` used `allCases.firstIndex(of:)`; Kotlin enums expose the same declaration index
 * as [Enum.ordinal] (same as the `WindDirection` port), so the cases must stay in low → high order.
 */
@Serializable
data class WeatherAlert(
    val level: Level,
    /** AEMET's event title, e.g. "Aviso de temperaturas máximas de nivel naranja". */
    val event: String,
    /** The phenomenon, e.g. "Temperatura máxima" (from the `parametro` field's middle component). */
    val phenomenon: String?,
    /** 6-digit warning-zone code, e.g. "610401". */
    val zona: String,
    /** Human-readable zone name, e.g. "Valle del Almanzora y Los Vélez". */
    val areaDesc: String?,
    @Serializable(with = InstantEpochMillisSerializer::class)
    val onset: Instant?,
    @Serializable(with = InstantEpochMillisSerializer::class)
    val expires: Instant?,
) {
    /** AEMET's four warning levels, low → high. */
    @Serializable
    enum class Level {
        VERDE, AMARILLO, NARANJA, ROJO;

        /** Higher is more severe; `verde` means no active warning. */
        val rank: Int get() = ordinal
    }

    /** Stable identity, mirroring Swift's `Identifiable` (`id: String`). */
    val id: String get() = "$zona-$event"

    /** Province INE code this warning's zone belongs to (digits 3–4 of the zone code). */
    val provinceCode: String
        get() = if (zona.length >= 4) zona.drop(2).take(2) else ""

    /** A real, still-current warning (amber or above and not expired). */
    fun isActive(now: Instant = Instant.now()): Boolean =
        level.rank >= Level.AMARILLO.rank && (expires?.let { !it.isBefore(now) } ?: true)

    /**
     * A one or two word Spanish summary of the warning for a glance: "Calor", "Tormentas", "Nieve".
     * AEMET's own phrasing ("Temperatura máxima", "Fenómenos costeros") is longer than the compact
     * hint beside "MÁS" wants, so it is mapped to a plain word. Weather phenomena are checked before
     * the temperature cases so a "rachas máximas de viento" reads as "Viento", not "Calor". Unknown
     * phenomena fall back to their first word, or a generic "Aviso".
     */
    val shortLabel: String
        get() {
            val text = ((phenomenon ?: "") + " " + event).lowercase()
            fun has(vararg needles: String): Boolean = needles.any { text.contains(it) }
            return when {
                has("costero", "costera") -> "Costa"
                has("tormenta") -> "Tormentas"
                has("nevada", "nieve") -> "Nieve"
                has("lluvia", "precipitaci", "aguacero") -> "Lluvia"
                has("viento", "racha") -> "Viento"
                has("niebla") -> "Niebla"
                has("polvo", "calima") -> "Calima"
                has("alud") -> "Aludes"
                has("incend") -> "Incendios"
                has("oleaje", "marejada", "temporal marítimo") -> "Oleaje"
                has("máxim", "altas temp", "calor") -> "Calor"
                has("mínim", "bajas temp", "helada", "frío", "frio") -> "Frío"
                // Swift's `.capitalized` uppercases the first letter and lowercases the rest of the
                // word, so mirror both halves (not just replaceFirstChar) for a faithful fallback.
                else -> phenomenon?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() }
                    ?.lowercase()?.replaceFirstChar { it.uppercase() }
                    ?: "Aviso"
            }
        }
}

/**
 * The most severe still-active warning for a province, if any — what a card surfaces.
 * Swift models this as `extension Array where Element == WeatherAlert`.
 */
fun List<WeatherAlert>.topActive(forProvince: String, now: Instant = Instant.now()): WeatherAlert? =
    filter { it.provinceCode == forProvince && it.isActive(now) }
        .maxByOrNull { it.level.rank }

/**
 * Maps a province (INE code) to the AEMET avisos "area" code its CAP bulletin is published under.
 * Verified empirically against every published area; the middle two digits of each zone code are
 * the province INE, except for the island communities whose zones use island digits — so Balears
 * (07) and the two Canary provinces (35, 38) are mapped to their community's area explicitly.
 *
 * Kept with the model (rather than in the net layer) because it's pure data. It's consumed by the
 * fetch side to know which CAP bulletin to download, which lands later.
 */
object AvisoArea {
    fun forProvincia(code: String): String? = map[code]

    val map: Map<String, String> = mapOf(
        "01" to "75", "02" to "68", "03" to "77", "04" to "61", "05" to "67", "06" to "70", "07" to "64",
        "08" to "69", "09" to "67", "10" to "70", "11" to "61", "12" to "77", "13" to "68", "14" to "61",
        "15" to "71", "16" to "68", "17" to "69", "18" to "61", "19" to "68", "20" to "75", "21" to "61",
        "22" to "62", "23" to "61", "24" to "67", "25" to "69", "26" to "76", "27" to "71", "28" to "72",
        "29" to "61", "30" to "73", "31" to "74", "32" to "71", "33" to "63", "34" to "67", "35" to "65",
        "36" to "71", "37" to "67", "38" to "65", "39" to "66", "40" to "67", "41" to "61", "42" to "67",
        "43" to "69", "44" to "62", "45" to "68", "46" to "77", "47" to "67", "48" to "75", "49" to "67",
        "50" to "62", "51" to "78", "52" to "79",
    )
}
