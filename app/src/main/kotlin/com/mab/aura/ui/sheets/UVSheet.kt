package com.mab.aura.ui.sheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mab.aura.R
import com.mab.aura.core.scale.UVBands
import com.mab.aura.core.uv.UVIndex
import com.mab.aura.ui.theme.Palette
import kotlin.math.min

/**
 * The UV reference sheet behind the UV card, ported from `AuraUVSheet` in `AuraScaleSheets.swift`: the five
 * WHO bands with today's maximum highlighted (labelled "Máx. hoy", since AEMET's UV is a daily-max forecast,
 * not a live value), and an optional cloud note when the sky is holding the live UV below that maximum.
 *
 * The per-band SF Symbol the Swift sheet showed is left out, exactly as on the UV card — Material core has no
 * such weather glyph and `UVIndex.glyph` was not ported.
 */
@Composable
internal fun AuraUVSheet(uvIndex: UVIndex, cloudy: Boolean, onClose: () -> Unit) {
    AuraScaleSheet(
        title = stringResource(R.string.sheet_uv_title),
        subtitle = stringResource(R.string.sheet_uv_subtitle, uvIndex.value, uvIndex.bandName.lowercase()),
        footnote = stringResource(R.string.sheet_uv_footnote),
        barColors = UVBands.bands.map { Palette.uvIndex(it.mid) },
        markerFraction = min(uvIndex.value.toDouble(), 11.0) / 11,
        markerLabel = stringResource(R.string.sheet_uv_marker, uvIndex.value),
        note = if (cloudy) stringResource(R.string.sheet_uv_cloud_note) else null,
        onClose = onClose,
    ) {
        UVBands.bands.forEach { band ->
            AuraScaleRow(
                color = Palette.uvIndex(band.mid),
                badge = band.rangeText,
                name = band.name,
                detail = band.advice,
                isCurrent = band.contains(uvIndex.value),
                currentLabel = stringResource(R.string.sheet_uv_max_today),
            )
        }
    }
}
