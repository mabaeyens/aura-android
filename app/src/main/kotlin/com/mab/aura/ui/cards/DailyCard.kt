package com.mab.aura.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.aura.core.model.DaySnapshot
import com.mab.aura.ui.AnimatedConditionGlyph
import com.mab.aura.ui.theme.Palette
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * The multi-day "Próximos días" list, ported from `AuraDailyCard` in `AuraAppCards.swift`. One row per day:
 * weekday, condition glyph over its rain chance, the low, a range bar, and the high. Every row's range bar
 * is drawn on the week's shared low→high scale, so a warm day's bar sits visibly to the right of a cold
 * day's, the way Apple Weather charts a week.
 *
 * The glyph's day/night is read off the sky code's own "n" suffix (as [AuraHourlyCard] does), since
 * [AnimatedConditionGlyph] takes an explicit flag where the Swift symbol lookup read the code directly. The
 * glyph is the animated Meteocons art (the same set the hourly strip plays), so it carries its own fills and
 * needs no content colour; it falls back to the static glyph for any condition without an animation.
 */
@Composable
fun AuraDailyCard(
    days: List<DaySnapshot>,
    size: AuraSize,
    modifier: Modifier = Modifier,
) {
    // The week's overall low and high — the shared scale every row's range bar is drawn on.
    val weekLo = days.mapNotNull { it.min }.minOrNull() ?: 0
    val weekHi = days.mapNotNull { it.max }.maxOrNull() ?: 1

    AuraSection("Próximos días".uppercase(), size, modifier = modifier) {
        AuraCard(size) {
            Column(verticalArrangement = Arrangement.spacedBy(size.rowGap)) {
                days.forEach { d ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = weekday(d.date),
                            fontSize = size.bodySize - 1,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.width(52.dp),
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.width(40.dp),
                        ) {
                            AnimatedConditionGlyph(
                                sky = d.sky,
                                isNight = d.sky?.endsWith("n") == true,
                                slot = size.iconSize,
                            )
                            // Always render the precip line (a blank when there's no meaningful chance) so
                            // every row is exactly the same height, rain or not.
                            Text(
                                text = d.probPrecip?.let { if (it >= 10) "$it%" else " " } ?: " ",
                                fontSize = size.smallSize - 2,
                                fontWeight = FontWeight.SemiBold,
                                color = auraPrecipColor,
                                maxLines = 1,
                            )
                        }

                        Text(
                            text = fmt(d.min),
                            fontSize = size.bodySize - 1,
                            fontWeight = FontWeight.Medium,
                            color = Palette.temperature(d.min),
                            textAlign = TextAlign.End,
                            style = tabularDigits,
                            modifier = Modifier.width(46.dp),
                        )

                        RangeBar(d, weekLo, weekHi)

                        Text(
                            text = fmt(d.max),
                            fontSize = size.bodySize - 1,
                            fontWeight = FontWeight.Bold,
                            color = Palette.temperature(d.max),
                            textAlign = TextAlign.Start,
                            style = tabularDigits,
                            modifier = Modifier.width(46.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One day's temperature range as a coloured segment inside a faint full-width track, positioned by where
 * [min, max] falls within the week's span and filled with that range's own temperature colours. The
 * [BoxWithConstraints] gives the pixel width the Swift `GeometryReader` provided, so the segment can be
 * offset and sized by fraction.
 */
@Composable
private fun RowScope.RangeBar(d: DaySnapshot, weekLo: Int, weekHi: Int) {
    val span = max(1, weekHi - weekLo)
    val lo = d.min ?: weekLo
    val hi = d.max ?: weekHi
    val startF = (lo - weekLo).toFloat() / span
    val endF = (hi - weekLo).toFloat() / span
    val barH = 6.dp
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .height(barH),
    ) {
        val w = maxWidth
        // The faint full-width track.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barH)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
        )
        // The coloured segment, offset to where the day's low sits and filled with its own temperature ramp.
        Box(
            modifier = Modifier
                .padding(start = w * startF)
                .width((w * (endF - startF)).coerceAtLeast(barH))
                .height(barH)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(Palette.temperatureGradient(lo, hi))),
        )
    }
}

private fun fmt(v: Int?): String = v?.let { "$it°" } ?: "—"

// Tabular figures so the temperature columns line up digit-for-digit, matching the Swift `.monospacedDigit`.
private val tabularDigits = TextStyle(fontFeatureSettings = "tnum")

// Abbreviated weekday in es-ES, capitalised, e.g. "Mié". Uses the system zone to match Swift's
// `Calendar.current`; the trailing period Java's CLDR adds is dropped for the compact card look.
private val weekdayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", Locale("es", "ES")).withZone(ZoneId.systemDefault())

private fun weekday(date: java.time.Instant): String =
    weekdayFormatter.format(date).removeSuffix(".").replaceFirstChar { it.uppercase() }
