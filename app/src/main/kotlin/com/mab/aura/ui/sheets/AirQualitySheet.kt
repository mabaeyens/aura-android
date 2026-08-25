package com.mab.aura.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.aura.core.air.AirComponent
import com.mab.aura.core.air.AirQuality
import com.mab.aura.core.scale.AirICA
import com.mab.aura.ui.theme.Palette
import java.time.Instant

/**
 * The ICA reference sheet behind the air-quality card, ported from `AuraAirQualitySheet` in
 * `AuraScaleSheets.swift`: the six ICA levels with the current one highlighted, then a richer per-pollutant
 * breakdown — each of the five pollutants on its own 1…6 ramp — for when you want to know *why* the index is
 * where it is. The marker sits at the middle of the current category's band, matching the Swift.
 */
@Composable
internal fun AuraAirQualitySheet(airQuality: AirQuality, now: Instant, onClose: () -> Unit) {
    AuraScaleSheet(
        title = "Índice de calidad del aire",
        subtitle = subtitle(airQuality),
        footnote = "Índice ICA del Ministerio (MITECO): el peor de los contaminantes marca el nivel. Aura " +
            "toma cada contaminante de la estación más cercana que lo mide (el O₃ y el SO₂ rara vez están " +
            "en la más próxima) y usa las medias con las que se elabora el ICA: 8 h para el O₃, 24 h para " +
            "las partículas. Por eso cada uno puede venir de una estación y una hora distintas, indicadas " +
            "abajo.",
        barColors = (1..6).map { Palette.airQuality(it) },
        markerFraction = (airQuality.category.toDouble() - 0.5) / 6,
        markerLabel = "Nivel ${airQuality.category}",
        onClose = onClose,
    ) {
        AirICA.levels.forEach { level ->
            AuraScaleRow(
                color = Palette.airQuality(level.category),
                badge = "${level.category}",
                name = level.name,
                detail = level.advice,
                isCurrent = level.category == airQuality.category,
            )
        }
        ComponentSection(airQuality, now)
    }
}

/**
 * The five ICA pollutants, each on its own 1…6 ramp — the ones this station measures with a value and a
 * marker, the ones it does not with a greyed rail and "No medido". A richer read than the card's row of chips.
 */
@Composable
private fun ComponentSection(airQuality: AirQuality, now: Instant) {
    // Keep the first reading per pollutant (Swift's `uniquingKeysWith: { a, _ in a }`).
    val measured = airQuality.components.associateBy({ it.pollutant }, { it })
    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "POR CONTAMINANTE",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp,
                color = Color.White.copy(alpha = 0.72f),
            )
            Text(
                text = "Cada uno, de la estación más cercana que lo mide.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        AirComponent.order.forEach { token ->
            AirComponentScale(
                token = token,
                component = measured[token],
                isDriver = token == airQuality.pollutant,
                now = now,
            )
        }
    }
}

private fun subtitle(airQuality: AirQuality): String {
    val by = airQuality.pollutantLabel?.let { ", por $it" } ?: ""
    return "Ahora: ${airQuality.categoryName.lowercase()} (nivel ${airQuality.category})$by, en ${airQuality.station}."
}
