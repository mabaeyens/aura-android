package com.mab.aura.core.wind

/**
 * The 0–12 Beaufort scale in km/h (the unit AEMET reports), with the Spanish names AEMET uses and a short
 * visible effect for each force.
 *
 * Direct port of the `Beaufort` enum in `AuraScaleSheets.swift`. On iOS it lives inside a SwiftUI file, but
 * it is pure logic (no UI), so on Android it belongs in `:core`: [ForecastPhrase] and the eventual wind
 * scale sheet both read it. Swift's `enum Beaufort` with only static members is a Kotlin `object`; the
 * nested `Step` struct becomes a nested data class.
 */
object Beaufort {
    /** One rung of the scale: its force number, Spanish name, km/h band, and a one-line visible effect. */
    data class Step(
        val force: Int,
        val name: String,
        val lo: Int,
        /** null = open-ended top force (12). */
        val hi: Int?,
        val effect: String,
    ) {
        /** The band as text: "20–28 km/h", "menos de 1 km/h" for calm, "más de 118 km/h" for the top. */
        val rangeText: String
            get() = when {
                force == 0 -> "menos de 1 km/h"
                hi != null -> "$lo–$hi km/h"
                else -> "más de $lo km/h"
            }

        /** A representative speed for the row's colour, on the wind-colour ramp. */
        val midKmh: Int get() = hi?.let { (lo + it) / 2 } ?: (lo + 20)
    }

    val scale: List<Step> = listOf(
        Step(0, "Calma", 0, 0, "El humo sube vertical."),
        Step(1, "Ventolina", 1, 5, "El humo indica la dirección del viento."),
        Step(2, "Flojito", 6, 11, "Se nota en la cara; se mueven las hojas."),
        Step(3, "Flojo", 12, 19, "Ondea una bandera ligera."),
        Step(4, "Bonancible", 20, 28, "Levanta polvo y papeles."),
        Step(5, "Fresquito", 29, 38, "Se balancean los arbustos."),
        Step(6, "Fresco", 39, 49, "Silban los cables; cuesta el paraguas."),
        Step(7, "Frescachón", 50, 61, "Cuesta caminar contra el viento."),
        Step(8, "Temporal", 62, 74, "Se rompen ramas pequeñas."),
        Step(9, "Temporal fuerte", 75, 88, "Daños leves en edificios."),
        Step(10, "Temporal duro", 89, 102, "Arranca árboles; daños de consideración."),
        Step(11, "Temporal muy duro", 103, 117, "Destrozos generalizados."),
        Step(12, "Temporal huracanado", 118, null, "Devastación."),
    )

    /** The Beaufort force for a wind speed in km/h, or -1 when there's no reading. */
    fun force(forKmh: Int?): Int {
        val v = forKmh ?: return -1
        for (step in scale) if (v <= (step.hi ?: Int.MAX_VALUE)) return step.force
        return 12
    }
}
