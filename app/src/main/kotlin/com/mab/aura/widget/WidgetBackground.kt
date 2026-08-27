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
 * How much of the wide art's sky to trim off the **top** on a **wide/short** tile (e.g. 4x2) before Glance
 * centre-crops it. The art is 4:3 (a tall frame with a lot of sky); a short tile would otherwise centre-crop
 * to a thin middle band that is mostly sky, so dropping this fraction from the top first shifts that crop down
 * to the scene (skyline, hills). A **tall** tile (e.g. 2x4) keeps its taller crop, which already shows plenty
 * of scene, so it gets no trim — the choice is made per tile in the widget composition where the tile size is
 * known (see `WidgetContent` in AuraGlanceWidget). 0.35 keeps the lower 65% of the art.
 */
internal const val SKY_CUT_WIDE = 0.35f

/**
 * The widget's background: the real **wide** hero scene for this sky and time of day, or null to fall back to
 * the procedural [skyGradientBitmap].
 *
 * Uses the same `HeroBackground` resolver the phone's `AuraSky` uses, on the wide grid: the per-condition wide
 * art if it shipped, else the conditionless day/night base, else null. Unlike the phone, the Glance widget
 * can't draw the live sun/moon over the art (RemoteViews has no Canvas), so the scene stands on its own — the
 * time of day and condition are what the baked art already carries; the moving sun is the one phone-only touch.
 *
 * Returns the **full** art. The sky trim is tile-shape dependent (see [SKY_CUT_WIDE]) and the tile size is only
 * known in the composition, so [cropTopSky] is applied there, not here.
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
    return HeroImages.loadWide(context, name)
}

/**
 * Return the art with its top [cut] fraction removed, keeping the lower band. [Bitmap.createBitmap] with a
 * sub-region hands back a new bitmap for that rectangle, so the original is left untouched. A zero or
 * out-of-range cut returns the source unchanged — that is the tall-tile path, which trims nothing.
 */
internal fun cropTopSky(src: Bitmap, cut: Float): Bitmap {
    val px = (src.height * cut).toInt()
    if (px <= 0 || px >= src.height) return src
    return Bitmap.createBitmap(src, 0, px, src.width, src.height - px)
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
