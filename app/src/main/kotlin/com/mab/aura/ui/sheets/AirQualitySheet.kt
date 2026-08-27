package com.mab.aura.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import com.mab.aura.R
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
        title = stringResource(R.string.sheet_aqi_title),
        subtitle = subtitle(airQuality, LocalContext.current),
        footnote = stringResource(R.string.sheet_aqi_footnote),
        barColors = (1..6).map { Palette.airQuality(it) },
        markerFraction = (airQuality.category.toDouble() - 0.5) / 6,
        markerLabel = stringResource(R.string.sheet_aqi_marker, airQuality.category),
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
                text = stringResource(R.string.sheet_aqi_by_pollutant_header),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp,
                color = Color.White.copy(alpha = 0.72f),
            )
            Text(
                text = stringResource(R.string.sheet_aqi_by_pollutant_note),
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

private fun subtitle(airQuality: AirQuality, context: Context): String {
    val by = airQuality.pollutantLabel?.let { context.getString(R.string.sheet_aqi_subtitle_by, it) } ?: ""
    return context.getString(
        R.string.sheet_aqi_subtitle,
        airQuality.categoryName.lowercase(),
        airQuality.category,
        by,
        airQuality.station,
    )
}
