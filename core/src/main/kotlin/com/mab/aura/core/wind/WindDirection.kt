package com.mab.aura.core.wind

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * 16-point compass rose with Spanish (rosa de los vientos) abbreviations and names.
 *
 * Meteorological convention: the bearing is the direction the wind blows *from*.
 *
 * Direct port of `WindDirection.swift`. Swift backs the enum with an `Int` raw value (n = 0 …
 * nno = 15); Kotlin enums have no raw value, so the port uses the built-in [ordinal], which is the
 * declaration index — identical as long as the cases stay in this order. Swift's failable
 * `init?(aemet:)` becomes [fromAemet] returning null; the non-failable `init(degrees:)` becomes
 * [fromDegrees]. Serializable so it can be a `WeatherSnapshot` field; kotlinx.serialization encodes
 * an enum by its name, which is fine here since only Aura reads its own snapshots back.
 */
@Serializable
enum class WindDirection {
    N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSO, SO, OSO, O, ONO, NO, NNO;

    /** Spanish abbreviation, e.g. "ONO". */
    val abbreviation: String get() = abbreviations[ordinal]

    /** Full Spanish name, e.g. "Oesnoroeste". */
    val spanishName: String get() = names[ordinal]

    /** Bearing in degrees at the centre of this sector (N = 0, E = 90, ...). */
    val degrees: Double get() = ordinal * 22.5

    companion object {
        private val abbreviations = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO",
        )

        private val names = listOf(
            "Norte", "Nornordeste", "Nordeste", "Estenordeste",
            "Este", "Estesudeste", "Sudeste", "Sursudeste",
            "Sur", "Sursudoeste", "Sudoeste", "Oessudoeste",
            "Oeste", "Oesnoroeste", "Noroeste", "Nornoroeste",
        )

        /** Nearest 16-point direction for a bearing in degrees. */
        fun fromDegrees(degrees: Double): WindDirection {
            // Kotlin's `%` on Double keeps the dividend's sign, matching Swift's truncatingRemainder.
            val wrapped = degrees % 360.0
            val positive = if (wrapped < 0) wrapped + 360 else wrapped
            // `positive` is always >= 0 here, so roundToInt (ties toward +inf) matches Swift's
            // `.rounded()` (ties away from zero). The % 16 folds 360° back onto N.
            val index = (positive / 22.5).roundToInt() % 16
            return entries[index]
        }

        /**
         * Match an AEMET direction abbreviation (e.g. "NO", "ONO"). Returns null for calm ("C") or an
         * unrecognised token.
         */
        fun fromAemet(code: String): WindDirection? {
            val token = code.trim().uppercase()
            return entries.firstOrNull { it.abbreviation == token }
        }
    }
}
