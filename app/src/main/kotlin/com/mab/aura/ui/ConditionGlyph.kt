package com.mab.aura.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.mab.aura.R
import com.mab.aura.core.icon.WeatherGlyph
import com.mab.aura.core.icon.WeatherIcon

/**
 * The condition icon, over the [WeatherGlyph] mapping in `:core`'s [WeatherIcon]. Ported from
 * `ConditionGlyph.swift`.
 *
 * iOS drew SF Symbols in **multicolour** (a yellow sun, grey clouds). Android has no SF Symbols, so this
 * renders **Meteocons** (MIT, Bas Milius) instead: full-colour vector drawables (`res/drawable/ic_wx_*`)
 * whose fills (yellow sun, gradient cloud, blue rain) are baked into the paths. They are drawn with [Image]
 * and are **not tinted**, so the colour survives — unlike the old monochrome Material glyphs, which had to
 * be tinted. Every condition now has colour art, including the day/night and storm variants iOS keeps.
 *
 * [tint] is therefore normally left null. It stays only as an escape hatch: passing a colour applies a flat
 * [ColorFilter] over the whole glyph (a silhouette), which nothing needs today but keeps the old signature
 * working. `LocalContentColor` no longer affects the glyph.
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
    val painter = painterResource(drawableFor(glyph))
    val colorFilter = tint?.let { ColorFilter.tint(it) }

    @Composable
    fun glyphAt(iconModifier: Modifier) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            colorFilter = colorFilter,
            modifier = iconModifier,
        )
    }

    if (slot != null) {
        Box(modifier = modifier.size(slot * 1.5f), contentAlignment = Alignment.Center) {
            glyphAt(Modifier.size(slot))
        }
    } else {
        glyphAt(modifier)
    }
}

/** The bundled Meteocons drawable for a [WeatherGlyph]. Each condition has its own file, including the
 *  day/night light-rain split and the three thunder variants (day / night / with-rain) that the earlier
 *  monochrome port had collapsed. `internal` so the Glance widget's hourly strip can reuse the exact same
 *  icon set (it can't call this Composable, but it can map a glyph to the same drawable via this function). */
internal fun drawableFor(glyph: WeatherGlyph): Int = when (glyph) {
    WeatherGlyph.CLEAR_DAY -> R.drawable.ic_wx_clear_day
    WeatherGlyph.CLEAR_NIGHT -> R.drawable.ic_wx_clear_night
    WeatherGlyph.FEW_CLOUDS_DAY -> R.drawable.ic_wx_few_clouds_day
    WeatherGlyph.FEW_CLOUDS_NIGHT -> R.drawable.ic_wx_few_clouds_night
    WeatherGlyph.CLOUDY -> R.drawable.ic_wx_cloudy
    WeatherGlyph.HAZE -> R.drawable.ic_wx_haze
    WeatherGlyph.LIGHT_RAIN_DAY -> R.drawable.ic_wx_light_rain
    WeatherGlyph.LIGHT_RAIN_NIGHT -> R.drawable.ic_wx_light_rain_night
    WeatherGlyph.RAIN -> R.drawable.ic_wx_rain
    WeatherGlyph.HEAVY_RAIN -> R.drawable.ic_wx_heavy_rain
    WeatherGlyph.SNOW -> R.drawable.ic_wx_snow
    WeatherGlyph.LIGHT_SNOW -> R.drawable.ic_wx_light_snow
    WeatherGlyph.THUNDER_DAY -> R.drawable.ic_wx_thunder_day
    WeatherGlyph.THUNDER_NIGHT -> R.drawable.ic_wx_thunder_night
    WeatherGlyph.THUNDER_RAIN -> R.drawable.ic_wx_thunder
}
