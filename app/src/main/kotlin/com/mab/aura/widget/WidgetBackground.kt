package com.mab.aura.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.mab.aura.ui.theme.Palette

/**
 * Bakes the app's per-condition sky gradient into a small bitmap for the Glance widget's background.
 *
 * Why a bitmap: Glance renders to RemoteViews, which has no gradient primitive — a `Brush` can't cross that
 * boundary. So the widget draws the same [Palette.skyGradientColors] ramp the app's `AuraSky` uses onto a
 * tiny bitmap (stretched to fill by the Image), instead of reusing the Compose sky. This is the documented
 * "no hero art → procedural sky" fallback (the wide hero art isn't in the repo yet).
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
