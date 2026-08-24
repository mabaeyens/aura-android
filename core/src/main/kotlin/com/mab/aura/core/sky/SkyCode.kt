package com.mab.aura.core.sky

/**
 * The nine weather categories an AEMET `estadoCielo` code maps to, and the classifier that assigns one.
 *
 * Ported from the `Palette.Sky` enum and `Palette.sky(forCode:)` in `Palette.swift`. The rest of `Palette`
 * is colour/gradient code (SwiftUI `Color`/`LinearGradient`), which is UI and stays in the `:app` module;
 * only this classification is pure logic, and [ForecastPhrase] needs it in `:core`. Swift returns a tuple
 * `(category, isNight)`; Kotlin has no tuple, so [classify] returns a small [Classification] data class.
 */
enum class SkyCategory {
    CLEAR, FEW_CLOUDS, CLOUDS, OVERCAST, RAIN, STORM, SNOW, FOG, UNKNOWN
}

object SkyCode {
    /** A classified sky code: its weather [category] plus whether the code carried the night suffix. */
    data class Classification(val category: SkyCategory, val isNight: Boolean)

    /** Categorise an AEMET `estadoCielo` code; [Classification.isNight] reflects the code's trailing "n". */
    fun classify(code: String?): Classification {
        if (code.isNullOrEmpty()) return Classification(SkyCategory.UNKNOWN, false)
        val isNight = code.endsWith("n")
        val base = if (isNight) code.dropLast(1) else code
        val category = when (base.toIntOrNull() ?: -1) {
            11, 17 -> SkyCategory.CLEAR
            12, 13 -> SkyCategory.FEW_CLOUDS
            14, 15 -> SkyCategory.CLOUDS
            16 -> SkyCategory.OVERCAST
            23, 24, 25, 26, 43, 44, 45, 46 -> SkyCategory.RAIN
            51, 52, 53, 54, 61, 62, 63, 64 -> SkyCategory.STORM
            33, 34, 35, 36, 71, 72, 73, 74 -> SkyCategory.SNOW
            81, 82, 83 -> SkyCategory.FOG
            else -> SkyCategory.UNKNOWN
        }
        return Classification(category, isNight)
    }
}
