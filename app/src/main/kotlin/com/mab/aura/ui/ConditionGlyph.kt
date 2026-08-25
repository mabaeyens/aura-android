package com.mab.aura.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.mab.aura.R
import com.mab.aura.core.icon.WeatherGlyph
import com.mab.aura.core.icon.WeatherIcon
import com.mab.aura.ui.theme.Palette

/**
 * The condition icon, over the [WeatherGlyph] mapping in `:core`'s [WeatherIcon]. Ported from
 * `ConditionGlyph.swift`.
 *
 * iOS drew SF Symbols in **multicolour** (a yellow sun, grey clouds). Android has no SF Symbols and the
 * standard Material Symbols are single-path monochrome, so this ships the ~12 needed glyphs as bundled
 * vector drawables (`res/drawable/ic_wx_*`) and tints them. Two of the Swift special cases carry over as
 * tint rules, because a flat monochrome glyph would otherwise read wrong:
 * - The clear-night moon is tinted [Palette.nightMoon] (blue), so night is unambiguous rather than a pale
 *   coin that reads as a daytime sun.
 * - The snowflake is forced white (it vanishes into the label colour on a light card otherwise).
 *
 * Everything else takes [tint], defaulting to the ambient `LocalContentColor` so the glyph sits correctly
 * wherever it is placed. Pass an explicit [tint] to override even the two special cases.
 *
 * Android note on sizing: SwiftUI let the caller set the size with `.font(...)`. Here the caller sizes it
 * with the [modifier] (e.g. `Modifier.size(20.dp)`). The optional [slot] mirrors the Swift `GlyphSlot`: it
 * draws the glyph at [slot] inside a fixed `slot * 1.5` **square**, so a wide rain cloud and a narrow sun
 * keep the same footprint and don't knock a temperature row out of line in the hour strip.
 */
@Composable
fun ConditionGlyph(
    sky: String?,
    isNight: Boolean,
    modifier: Modifier = Modifier,
    slot: Dp? = null,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    val glyph = WeatherIcon.glyph(sky, isNight)
    val resolvedTint = tint ?: when (glyph) {
        WeatherGlyph.CLEAR_NIGHT -> Palette.nightMoon
        WeatherGlyph.SNOW -> Color.White
        else -> LocalContentColor.current
    }

    if (slot != null) {
        Box(modifier = modifier.size(slot * 1.5f), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(drawableFor(glyph)),
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(slot),
            )
        }
    } else {
        Icon(
            painter = painterResource(drawableFor(glyph)),
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = modifier,
        )
    }
}

/** The bundled drawable for a [WeatherGlyph]. Day/night light-rain and the three thunder cases share a
 *  glyph — the monochrome Material set has no distinct sun-behind-rain or day/night storm variant. */
private fun drawableFor(glyph: WeatherGlyph): Int = when (glyph) {
    WeatherGlyph.CLEAR_DAY -> R.drawable.ic_wx_clear_day
    WeatherGlyph.CLEAR_NIGHT -> R.drawable.ic_wx_clear_night
    WeatherGlyph.FEW_CLOUDS_DAY -> R.drawable.ic_wx_few_clouds_day
    WeatherGlyph.FEW_CLOUDS_NIGHT -> R.drawable.ic_wx_few_clouds_night
    WeatherGlyph.CLOUDY -> R.drawable.ic_wx_cloudy
    WeatherGlyph.HAZE -> R.drawable.ic_wx_haze
    WeatherGlyph.LIGHT_RAIN_DAY, WeatherGlyph.LIGHT_RAIN_NIGHT -> R.drawable.ic_wx_light_rain
    WeatherGlyph.RAIN -> R.drawable.ic_wx_rain
    WeatherGlyph.HEAVY_RAIN -> R.drawable.ic_wx_heavy_rain
    WeatherGlyph.SNOW -> R.drawable.ic_wx_snow
    WeatherGlyph.LIGHT_SNOW -> R.drawable.ic_wx_light_snow
    WeatherGlyph.THUNDER_DAY, WeatherGlyph.THUNDER_NIGHT, WeatherGlyph.THUNDER_RAIN ->
        R.drawable.ic_wx_thunder
}
