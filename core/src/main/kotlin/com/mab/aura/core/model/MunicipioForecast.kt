package com.mab.aura.core.model

import kotlinx.serialization.Serializable

/**
 * Subset of AEMET's daily municipality forecast, enough to prove the pipeline end to end.
 * Expanded field-by-field as later phases need hourly data, wind, sky state, etc.
 *
 * Direct port of `Models.swift` (AuraKit's `MunicipioForecast`). Swift's `Decodable` becomes
 * kotlinx.serialization's [@Serializable]. Android note for anyone coming from Swift: `Codable`
 * tolerates a missing key on any optional automatically, but kotlinx.serialization only treats a
 * property as optional if it has a default. So every Swift `T?` here is `T? = null` — without the
 * default a missing key throws `MissingFieldException` instead of decoding as absent. The client's
 * `Json` is also configured with `ignoreUnknownKeys = true`, since AEMET sends far more fields than
 * this subset decodes.
 */
@Serializable
data class MunicipioForecast(
    val nombre: String,
    val provincia: String,
    val prediccion: Prediccion,
) {
    @Serializable
    data class Prediccion(
        val dia: List<Dia>,
    )

    @Serializable
    data class Dia(
        val fecha: String,
        val temperatura: MinMax? = null,
        val humedadRelativa: MinMax? = null,
        /** Sky state in coarse blocks (periodo like "00-24", "12-24"); `value` is the AEMET code. */
        val estadoCielo: List<SkyBlock>? = null,
        /** Wind in coarse blocks; daily `velocidad` is a plain integer (km/h), unlike the hourly feed. */
        val viento: List<WindBlock>? = null,
        /**
         * Precipitation probability in coarse blocks (%). `value` is a plain Int here (the hourly feed
         * uses a String). Blocks vary by day: days 0–1 carry the full "00-24"/"00-12"/… set, days 4–6
         * a single value with no `periodo`. The whole-day "00-24" can be stale (0 while an afternoon
         * block reads 55), so a representative daily chance is the max across blocks — see `dailyPrecip`.
         */
        val probPrecipitacion: List<ProbBlock>? = null,
    )

    @Serializable
    data class MinMax(
        val maxima: Int? = null,
        val minima: Int? = null,
    )

    @Serializable
    data class SkyBlock(
        val value: String,
        val periodo: String? = null,
        val descripcion: String? = null,
    )

    @Serializable
    data class WindBlock(
        val direccion: String? = null,
        val velocidad: Int? = null,
        val periodo: String? = null,
    )

    @Serializable
    data class ProbBlock(
        val value: Int? = null,
        val periodo: String? = null,
    )
}
