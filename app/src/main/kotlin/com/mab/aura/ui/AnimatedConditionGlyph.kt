package com.mab.aura.ui

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mab.aura.R
import com.mab.aura.core.icon.WeatherGlyph
import com.mab.aura.core.icon.WeatherIcon

/**
 * Animated variant of [ConditionGlyph], backed by the **Meteocons** Lottie animations (MIT, Bas Milius),
 * bundled as raw resources in `res/raw/wx_anim_*.json`. The sun's rays turn, clouds drift, rain falls.
 *
 * It falls back to the static [ConditionGlyph] for any condition that has no animated asset yet, so it is a
 * safe drop-in anywhere [ConditionGlyph] is used. Sizing mirrors [ConditionGlyph]: pass [slot] to draw the
 * animation inside a fixed `slot * 1.5` square (so a wide storm and a narrow sun keep the same footprint),
 * or size it with [modifier] directly.
 *
 * Android note (why this is a separate component, not a flag on [ConditionGlyph]): animation only exists in
 * the app. The Glance widget renders to RemoteViews, which cannot run a Lottie or an AnimatedVectorDrawable,
 * so the widget always uses the static drawables via `drawableFor`. Keeping the animated path in its own
 * composable makes that boundary obvious and keeps the widget code unable to reach it by accident.
 */
@Composable
fun AnimatedConditionGlyph(
    sky: String?,
    isNight: Boolean,
    modifier: Modifier = Modifier,
    slot: Dp? = null,
    contentDescription: String? = null,
) {
    val glyph = WeatherIcon.glyph(sky, isNight)
    val raw = lottieRawFor(glyph)

    // No animated asset for this condition yet: use the static glyph so callers never get a blank.
    if (raw == null) {
        ConditionGlyph(sky = sky, isNight = isNight, modifier = modifier, slot = slot, contentDescription = contentDescription)
        return
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(raw))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)

    if (slot != null) {
        // Same footprint box and the same GLYPH_FILL enlargement as the static ConditionGlyph, so the
        // animated and the fallback glyph render at the same size (the Lottie shares the 128 canvas and
        // ~75% fill of the static Meteocons).
        Box(modifier = modifier.size(slot * 1.5f), contentAlignment = Alignment.Center) {
            LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(slot * GLYPH_FILL))
        }
    } else {
        LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
    }
}

/** The bundled Meteocons Lottie for a [WeatherGlyph]. Every condition now has an animation, mirroring the
 *  one-file-per-condition drawable mapping in `drawableFor` (with the same day/night + storm splits). Kept
 *  nullable so the fallback in [AnimatedConditionGlyph] still holds if a future glyph arrives without one. */
@RawRes
private fun lottieRawFor(glyph: WeatherGlyph): Int? = when (glyph) {
    WeatherGlyph.CLEAR_DAY -> R.raw.wx_anim_clear_day
    WeatherGlyph.CLEAR_NIGHT -> R.raw.wx_anim_clear_night
    WeatherGlyph.FEW_CLOUDS_DAY -> R.raw.wx_anim_few_clouds_day
    WeatherGlyph.FEW_CLOUDS_NIGHT -> R.raw.wx_anim_few_clouds_night
    WeatherGlyph.CLOUDY -> R.raw.wx_anim_cloudy
    WeatherGlyph.HAZE -> R.raw.wx_anim_haze
    WeatherGlyph.LIGHT_RAIN_DAY -> R.raw.wx_anim_light_rain_day
    WeatherGlyph.LIGHT_RAIN_NIGHT -> R.raw.wx_anim_light_rain_night
    WeatherGlyph.RAIN -> R.raw.wx_anim_rain
    WeatherGlyph.HEAVY_RAIN -> R.raw.wx_anim_heavy_rain
    WeatherGlyph.SNOW -> R.raw.wx_anim_snow
    WeatherGlyph.LIGHT_SNOW -> R.raw.wx_anim_light_snow
    WeatherGlyph.THUNDER_DAY -> R.raw.wx_anim_thunder_day
    WeatherGlyph.THUNDER_NIGHT -> R.raw.wx_anim_thunder_night
    WeatherGlyph.THUNDER_RAIN -> R.raw.wx_anim_thunder
}
