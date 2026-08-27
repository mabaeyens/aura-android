package com.mab.aura.ui.sheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Context
import com.mab.aura.R
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
        title = stringResource(R.string.sheet_beaufort_title),
        subtitle = subtitle(snapshot, LocalContext.current),
        footnote = stringResource(R.string.sheet_beaufort_footnote),
        barColors = Beaufort.scale.map { Palette.wind(it.midKmh) },
        markerFraction = if (current >= 0) current.toDouble() / 12 else null,
        markerLabel = stringResource(R.string.sheet_beaufort_marker, snapshot.windSpeed ?: 0),
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

private fun subtitle(snapshot: WeatherSnapshot, context: Context): String {
    val v = snapshot.windSpeed ?: return context.getString(R.string.sheet_beaufort_no_wind)
    val f = Beaufort.force(v)
    val name = Beaufort.scale.firstOrNull { it.force == f }?.name?.lowercase() ?: ""
    val dir = snapshot.windDirection?.let { " " + context.getString(R.string.sheet_beaufort_direction, it.spanishName.lowercase()) } ?: ""
    return context.getString(R.string.sheet_beaufort_subtitle, v, dir, f, name)
}
