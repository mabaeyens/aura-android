package com.mab.aura.core.uv

import com.mab.aura.core.model.UVIForecast
import kotlinx.serialization.Serializable

/**
 * AEMET's forecast daily-maximum UV index for a location, under clear-sky conditions (product
 * `prediccion/especifica/uvi/{dia}`, dia 0 = today). A single integer per provincial capital, on the
 * standard WHO 0–11+ scale; the band names and protection advice follow AEMET/WHO.
 *
 * Direct port of the `UVIndex` value type in `UVIndex.swift` (the `UVIForecast` wire model from that
 * same file already lives in `core/model/`). The Swift `glyph` property is intentionally not ported
 * yet: it returns SF Symbol names, which mean nothing on Android. The equivalent Material Symbols (or
 * custom art) mapping is deferred to the `WeatherIcon` phase, once the icon set is actually chosen, so
 * no dead iOS glyph strings are carried in the meantime.
 */
@Serializable
data class UVIndex(
    /** Clear-sky daily-max UV index (0…11+). */
    val value: Int,
) {
    /** WHO band name in Spanish. */
    val bandName: String
        get() = when (value) {
            in Int.MIN_VALUE..2 -> "Bajo"
            in 3..5 -> "Moderado"
            in 6..7 -> "Alto"
            in 8..10 -> "Muy alto"
            else -> "Extremadamente alto"
        }

    /** A one-line protection cue for the band. */
    val advice: String
        get() = when (value) {
            in Int.MIN_VALUE..2 -> "Sin protección necesaria"
            in 3..5 -> "Gafas de sol y crema"
            in 6..7 -> "Protección recomendada"
            in 8..10 -> "Evita el sol del mediodía"
            else -> "Evita la exposición al sol"
        }

    companion object {
        /**
         * The UV index for an INE municipio code, from the parsed forecast cities, or null if the city
         * isn't listed or its value doesn't parse. AEMET keys each city by its INE code (`id`), so this
         * is an exact match — the same code the snapshot carries.
         */
        fun pick(ine: String, cities: List<UVIForecast.City>): UVIndex? {
            val city = cities.firstOrNull { it.id == ine } ?: return null
            return city.uv.toIntOrNull()?.let { UVIndex(it) }
        }
    }
}
