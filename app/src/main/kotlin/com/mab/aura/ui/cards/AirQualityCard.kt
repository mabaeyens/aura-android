package com.mab.aura.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mab.aura.core.air.AirComponent
import com.mab.aura.core.air.AirQuality
import com.mab.aura.ui.sheets.AuraAirQualitySheet
import com.mab.aura.ui.sheets.AuraDetailCard
import com.mab.aura.ui.theme.Palette
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The nearest-station air-quality card, ported from `AuraAirQualityCard` in `AuraAppCards.swift`: the 1–6
 * ICA category in its official colour, the category name, and — small — the driver pollutant, the station,
 * and how far it sits. On the phone it also shows the per-pollutant breakdown row (the five canonical ICA
 * pollutants, each in its own band colour, the one that drove the category ringed).
 *
 * The card is only built when a reading resolved (`snapshot.airQuality != null`); the caller drops it
 * otherwise, so it never shows a placeholder. The Swift `.minimumScaleFactor` shrink-to-fit becomes plain
 * `maxLines` clipping here (Compose has no direct equivalent); the strings are short enough that this only
 * matters at extreme font scales.
 */
@Composable
fun AuraAirQualityCard(
    airQuality: AirQuality,
    size: AuraSize,
    modifier: Modifier = Modifier,
) {
    val color = Palette.airQuality(airQuality.category)
    val showComponents = airQuality.components.isNotEmpty()

    AuraSection("Calidad del aire".uppercase(), size, modifier = modifier) {
        AuraDetailCard(size, sheet = { onClose -> AuraAirQualitySheet(airQuality, Instant.now(), onClose) }) {
            Column(verticalArrangement = Arrangement.spacedBy(if (showComponents) 12.dp else 0.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(size.stackSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The ICA colour swatch carrying the 1–6 category number.
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${airQuality.category}",
                            fontSize = size.bodySize + 3,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = airQuality.categoryName,
                            fontSize = size.bodySize - 1,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 2,
                        )
                        Text(
                            text = detail(airQuality),
                            fontSize = size.smallSize - 1,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                        )
                    }
                }

                if (showComponents) ComponentsRow(airQuality, size)
            }
        }
    }
}

/**
 * The five ICA pollutants as an even row of colour-coded chips, each tinted by its own indicative ICA band
 * (the same palette as the headline swatch). Pollutants this station doesn't measure show greyed with a
 * dash. The pollutant that drove the overall category is ringed. A shared "µg/m³" caption sits beneath.
 */
@Composable
private fun ComponentsRow(airQuality: AirQuality, size: AuraSize) {
    // Measured values keyed by MITECO token, for O(1) lookup against the canonical five.
    val measured = airQuality.components.associateBy { it.pollutant }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AirComponent.order.forEach { token ->
                ComponentChip(
                    token = token,
                    component = measured[token],
                    isDriver = token == airQuality.pollutant,
                    size = size,
                )
            }
        }
        Text(
            text = "µg/m³",
            fontSize = size.smallSize - 2,
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/**
 * One pollutant chip: its label over the value over a band bar, all in the pollutant's ICA colour (grey
 * when the station doesn't measure it). The driver pollutant is ringed to tie it to the reason.
 */
@Composable
private fun RowScope.ComponentChip(
    token: String,
    component: AirComponent?,
    isDriver: Boolean,
    size: AuraSize,
) {
    val measured = component != null
    val color = Palette.airQuality(component?.icaCategory ?: 0) // 0 → grey (unmeasured)
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(color.copy(alpha = if (measured) 0.16f else 0.05f))
            .then(
                if (isDriver && measured) Modifier.border(1.5.dp, color.copy(alpha = 0.9f), shape)
                else Modifier,
            )
            .padding(vertical = 6.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = AirComponent.label(token),
            fontSize = size.smallSize - 1,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = if (measured) 0.7f else 0.35f),
            maxLines = 1,
        )
        Text(
            text = component?.valueText ?: "–",
            fontSize = size.bodySize - 1,
            fontWeight = FontWeight.Bold,
            color = if (measured) Color.White else Color.White.copy(alpha = 0.3f),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (measured) 1f else 0.45f)),
        )
    }
}

/**
 * "por O₃ · Retiro · a 1,7 km" — the driver pollutant, the station, and its distance (Spanish decimal
 * comma under 10 km, whole km beyond). A partial index (computed from fewer pollutants) gets a trailing
 * "· parcial". Ported from the Swift `detail`.
 */
private fun detail(airQuality: AirQuality): String {
    val parts = mutableListOf<String>()
    airQuality.pollutantLabel?.let { parts.add("por $it") }
    parts.add(airQuality.station)
    val km = airQuality.distanceKm
    parts.add(
        if (km < 10) {
            "a " + String.format(Locale.US, "%.1f", km).replace('.', ',') + " km"
        } else {
            "a ${km.roundToInt()} km"
        },
    )
    if (airQuality.partial) parts.add("parcial")
    return parts.joinToString(" · ")
}
