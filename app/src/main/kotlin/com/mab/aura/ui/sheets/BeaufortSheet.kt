package com.mab.aura.ui.sheets

import androidx.compose.runtime.Composable
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.scale.Beaufort
import com.mab.aura.ui.theme.Palette

/**
 * The Beaufort reference sheet behind the wind card, ported from `AuraBeaufortSheet` in
 * `AuraScaleSheets.swift`: the full 0–12 scale with the current force highlighted, so the km/h on the card
 * gains a scale to read it against. The ramp colours ride `Palette.wind` at each force's representative speed.
 */
@Composable
internal fun AuraBeaufortSheet(snapshot: WeatherSnapshot, onClose: () -> Unit) {
    val current = Beaufort.force(snapshot.windSpeed)
    AuraScaleSheet(
        title = "Escala de Beaufort",
        subtitle = subtitle(snapshot),
        footnote = "La fuerza se estima a partir de la velocidad media del viento; las rachas pueden ser " +
            "bastante mayores. Nombres de la escala según AEMET.",
        barColors = Beaufort.scale.map { Palette.wind(it.midKmh) },
        markerFraction = if (current >= 0) current.toDouble() / 12 else null,
        markerLabel = "${snapshot.windSpeed ?: 0} km/h",
        onClose = onClose,
    ) {
        Beaufort.scale.forEach { step ->
            AuraScaleRow(
                color = Palette.wind(step.midKmh),
                badge = "${step.force}",
                name = step.name,
                detail = "${step.rangeText} · ${step.effect}",
                isCurrent = step.force == current,
            )
        }
    }
}

private fun subtitle(snapshot: WeatherSnapshot): String {
    val v = snapshot.windSpeed ?: return "Ahora mismo no hay dato de viento."
    val f = Beaufort.force(v)
    val name = Beaufort.scale.firstOrNull { it.force == f }?.name?.lowercase() ?: ""
    val dir = snapshot.windDirection?.let { " del ${it.spanishName.lowercase()}" } ?: ""
    return "Ahora: $v km/h$dir — fuerza $f, $name."
}
