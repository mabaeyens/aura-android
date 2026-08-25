package com.mab.aura.ui.sheets

import androidx.compose.runtime.Composable
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
        title = "Índice ultravioleta",
        subtitle = "Máximo de hoy: ${uvIndex.value} — ${uvIndex.bandName.lowercase()}.",
        footnote = "Índice UV de la OMS: la radiación solar máxima prevista para hoy con cielo despejado. " +
            "Cuanto más alto, antes se quema la piel. Las nubes lo bajan; la nieve, el agua y la altitud lo suben.",
        barColors = UVBands.bands.map { Palette.uvIndex(it.mid) },
        markerFraction = min(uvIndex.value.toDouble(), 11.0) / 11,
        markerLabel = "UV ${uvIndex.value}",
        note = if (cloudy) "Ahora el cielo está nublado y baja el UV por debajo de este máximo." else null,
        onClose = onClose,
    ) {
        UVBands.bands.forEach { band ->
            AuraScaleRow(
                color = Palette.uvIndex(band.mid),
                badge = band.rangeText,
                name = band.name,
                detail = band.advice,
                isCurrent = band.contains(uvIndex.value),
                currentLabel = "Máx. hoy",
            )
        }
    }
}
