package com.mab.aura.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.mab.aura.core.hero.HeroBackground
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.ui.sky.HeroImages
import com.mab.aura.ui.theme.Palette
import java.time.Instant

/**
 * How much of the wide art's sky to trim off the **top** before the widget shows it. The art is 4:3 (a tall
 * frame with a lot of sky); the Glance tile is short and wide, so Glance centre-crops it to the middle band.
 * Left alone that middle band is mostly sky. Dropping this fraction from the top first shifts the centre-crop
 * downward, so the tile reads more of the scene (skyline, hills) and less empty sky. Tune to taste; 0.2 keeps
 * the lower 80% of the art.
 */
private const val SKY_CUT = 0.2f

/**
 * The widget's background: the real **wide** hero scene for this sky and time of day, or null to fall back to
 * the procedural [skyGradientBitmap].
 *
 * Uses the same `HeroBackground` resolver the phone's `AuraSky` uses, on the wide grid: the per-condition wide
 * art if it shipped, else the conditionless day/night base, else null. Unlike the phone, the Glance widget
 * can't draw the live sun/moon over the art (RemoteViews has no Canvas), so the scene stands on its own — the
 * time of day and condition are what the baked art already carries; the moving sun is the one phone-only touch.
 *
 * The loaded art has its top [SKY_CUT] of sky trimmed so the widget favours the scene over empty sky (see the
 * constant's note). Glance has no crop-alignment knob, so this is done here on the bitmap, not in the layout.
 */
internal fun wideHeroBitmap(
    context: Context,
    snapshot: WeatherSnapshot,
    now: Instant,
    family: HeroBackground.Family,
): Bitmap? {
    val available = HeroImages.availableWideNames(context)
    val name = HeroBackground.wideName(snapshot, now, family) { it in available }
        ?: HeroBackground.wideBaseName(snapshot, now, family)?.takeIf { it in available }
        ?: return null
    val full = HeroImages.loadWide(context, name) ?: return null
    return cropTopSky(full)
}

/**
 * Return the art with its top [SKY_CUT] removed, keeping the lower band. [Bitmap.createBitmap] with a
 * sub-region hands back a new bitmap for that rectangle, so the original is left untouched. A zero or
 * out-of-range cut just returns the source unchanged (defensive; SKY_CUT is a small constant today).
 */
private fun cropTopSky(src: Bitmap): Bitmap {
    val cut = (src.height * SKY_CUT).toInt()
    if (cut <= 0 || cut >= src.height) return src
    return Bitmap.createBitmap(src, 0, cut, src.width, src.height - cut)
}

/**
 * Bakes the app's per-condition sky gradient into a small bitmap for the Glance widget's background — the
 * fallback when no wide hero art matches (an unknown sky, or a family with no base for it).
 *
 * Why a bitmap: Glance renders to RemoteViews, which has no gradient primitive — a `Brush` can't cross that
 * boundary. So the widget draws the same [Palette.skyGradientColors] ramp the app's `AuraSky` uses onto a
 * tiny bitmap (stretched to fill by the Image), instead of reusing the Compose sky.
 *
 * The bitmap is deliberately small (a vertical gradient needs no width); the widget's Image stretches it with
 * `ContentScale.FillBounds`, so a few pixels wide is enough and keeps RemoteViews' memory budget untouched.
 */
internal fun skyGradientBitmap(code: String?, width: Int = 8, height: Int = 512): Bitmap {
    val stops = Palette.skyGradientColors(code)
    val top = stops.first().toArgb()
    val bottom = stops.last().toArgb()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
    }
    Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bitmap
}
