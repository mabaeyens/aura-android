package com.mab.aura.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mab.aura.R
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.ui.AuraWindRose
import com.mab.aura.ui.sheets.AuraBeaufortSheet
import com.mab.aura.ui.sheets.AuraDetailCard

/**
 * The full-width wind card, ported from `AuraWindCard` in `AuraAppCards.swift`: the [AuraWindRose] compass
 * on the left (a weather-vane needle over a nautical rose), with the speed, the direction spelled out, and
 * any gusts beside it. The same rose the Swift phone card drew — reused here so the wind reads identically.
 *
 * The Swift card leaned on `.minimumScaleFactor` to shrink the text before it truncated; Compose has no
 * direct equivalent, so the text simply wraps/clips at the given `maxLines` (the strings are short — a
 * speed, a direction name, a gust figure — so this is not a real constraint on a phone-width card).
 */
@Composable
fun AuraWindCard(
    snapshot: WeatherSnapshot,
    size: AuraSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AuraSection(stringResource(R.string.card_wind_title).uppercase(), size, modifier = modifier) {
        AuraDetailCard(size, sheet = { onClose -> AuraBeaufortSheet(snapshot, onClose) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(size.stackSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuraWindRose(
                    windSpeed = snapshot.windSpeed,
                    windDirection = snapshot.windDirection,
                    modifier = Modifier.size(100.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = snapshot.windSpeed?.let { "$it km/h" } ?: "—",
                        fontSize = size.bodySize + 6,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        text = directionText(snapshot, context),
                        fontSize = size.smallSize,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 2,
                    )
                    snapshot.windGust?.let { gust ->
                        Text(
                            text = stringResource(R.string.card_wind_gust, gust),
                            fontSize = size.smallSize,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "del Sudoeste · 225°", or "En calma" when there's no measurable wind. The full name reads clearly; the
 * numeric bearing (the reported 16-point sector, in degrees) rides beside it. Ported from the Swift
 * `directionText`.
 */
private fun directionText(snapshot: WeatherSnapshot, context: Context): String {
    val dir = snapshot.windDirection
    if (dir == null || (snapshot.windSpeed ?: 0) <= 0) return context.getString(R.string.card_wind_calm)
    return context.getString(R.string.card_wind_direction, dir.spanishName, dir.degrees.toInt())
}
