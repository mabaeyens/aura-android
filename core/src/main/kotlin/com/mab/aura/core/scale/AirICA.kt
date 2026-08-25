package com.mab.aura.core.scale

/**
 * The six ICA levels with their official Spanish names and a general-population recommendation. Reference
 * data behind the air-quality card's tap-through sheet; ported from the `AirICA` enum in
 * `AuraScaleSheets.swift`. The level names match `AirQuality.categoryName`; the row colour comes from
 * `Palette.airQuality` in `:app`.
 */
object AirICA {
    data class Level(val category: Int, val name: String, val advice: String)

    val levels: List<Level> = listOf(
        Level(1, "Buena", "Calidad del aire ideal para cualquier actividad al aire libre."),
        Level(2, "Razonablemente buena", "Se puede hacer vida normal al aire libre."),
        Level(3, "Regular", "Los grupos sensibles pueden notar molestias leves."),
        Level(4, "Desfavorable", "Los grupos sensibles deberían reducir el esfuerzo prolongado al aire libre."),
        Level(5, "Muy desfavorable", "Evita el ejercicio intenso al aire libre; los grupos sensibles, mejor en interiores."),
        Level(6, "Extremadamente desfavorable", "Evita la actividad física al aire libre."),
    )
}
