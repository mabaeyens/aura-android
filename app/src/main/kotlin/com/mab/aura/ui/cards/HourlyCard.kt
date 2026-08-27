package com.mab.aura.ui.cards

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.aura.R
import com.mab.aura.core.model.HourSlot
import com.mab.aura.ui.AnimatedConditionGlyph
import com.mab.aura.ui.theme.Palette

/**
 * The horizontal "Próximas horas" strip, ported from `AuraHourlyCard` in `AuraAppCards.swift`. Five hour
 * columns fit the card width and the strip scrolls to the rest of the day; each column is hour, condition
 * glyph, temperature, and (only when some hour carries a chance) rain percent, stacked so the rows line up
 * across every column.
 *
 * Two Android-specific choices:
 *
 * 1. **Columns, not a measured [androidx.compose.foundation.layout] Grid.** SwiftUI used a `Grid` and
 *    measured a column width so five filled the viewport. Here a [BoxWithConstraints] reads the card's inner
 *    width and each column takes a fifth of it; the rows still align because every column has the identical
 *    stack and the glyph sits in a fixed-footprint [ConditionGlyph] slot, so a wide rain cloud and a narrow
 *    sun don't knock the temperature row out of line.
 * 2. **Glyph day/night from the code.** SwiftUI drew a multicolour SF Symbol straight from the sky code.
 *    [ConditionGlyph] takes an explicit `isNight`, so it's read back off the code's own "n" suffix here,
 *    matching what the Swift symbol lookup did. The strip plays the animated Meteocons glyph via
 *    [AnimatedConditionGlyph]; it is full-colour, so no content colour is set around it.
 *
 * [scrolls] mirrors the Swift flag: on device (`true`) the strip scrolls; off (`false`) the first five hours
 * spread edge to edge, for the offline render path that can't lay out a horizontal scroll.
 */
@Composable
fun AuraHourlyCard(
    hours: List<HourSlot>,
    size: AuraSize,
    modifier: Modifier = Modifier,
    scrolls: Boolean = true,
) {
    // True when at least one hour carries a rain chance — otherwise the precip row is empty and dropped, so a
    // dry strip doesn't reserve a band of empty space at the card's bottom.
    val showPrecip = hours.any { (it.precipProb ?: 0) > 0 }

    AuraSection(stringResource(R.string.card_hourly_title).uppercase(), size, modifier = modifier) {
        AuraCard(size) {
            if (scrolls) {
                // The card's inner width is the viewport; a fifth of it is one column, so five fit and the
                // rest of the day scrolls past the right edge.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columnWidth = maxWidth / 5
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        hours.forEach { h ->
                            HourColumn(h, size, showPrecip, Modifier.width(columnWidth))
                        }
                    }
                }
            } else {
                // Offline preview: the first five, each taking an even share of the width.
                Row(modifier = Modifier.fillMaxWidth()) {
                    hours.take(5).forEach { h ->
                        HourColumn(h, size, showPrecip, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One hour's column: label, glyph, temperature, and the optional rain percent, all centred. */
@Composable
private fun HourColumn(
    h: HourSlot,
    size: AuraSize,
    showPrecip: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        // Tighter than the original 12dp. The strip scrolls horizontally, so the whole Row is as tall as its
        // tallest column — a rainy hour that carries a precip line. Dry hours are top-anchored, so any slack
        // here shows as an empty band below their temperature; a smaller gap keeps that band to a minimum.
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.card_hourly_hour, h.hour),
            fontSize = size.smallSize,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        // The hourly strip plays the animated Meteocons glyphs (colour, in motion); any condition without an
        // animated asset falls back to the static colour glyph. Both carry their own fills, so no tint here.
        AnimatedConditionGlyph(
            sky = h.sky,
            isNight = h.sky?.endsWith("n") == true,
            slot = size.iconSize + 6.dp,
        )
        // Temperature and its rain chance sit together as one tight unit. The strip scrolls in a single
        // Row, so the Row's height is set by the tallest column — a rainy hour that carries a precip line.
        // A dry hour is top-anchored, so it would otherwise show that whole reserved line as empty space
        // under its temperature. Pairing them here, with only a hair of spacing and a small precip font,
        // keeps that reserved band down to the height of the little percentage text instead of a full line.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = h.temp?.let { "$it°" } ?: "—",
                fontSize = size.bodySize - 2,
                fontWeight = FontWeight.Bold,
                color = Palette.temperature(h.temp),
                textAlign = TextAlign.Center,
            )
            if (showPrecip) {
                Text(
                    text = h.precipProb?.let { if (it > 0) "$it%" else "" } ?: "",
                    fontSize = size.smallSize - 3,
                    fontWeight = FontWeight.SemiBold,
                    color = auraPrecipColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
