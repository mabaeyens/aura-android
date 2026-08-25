package com.mab.aura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.mab.aura.core.wind.WindDirection
import com.mab.aura.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The compass rose the wind card sits over, ported from `AuraWindCircular` + `WindRose` in
 * `AuraSunWindCards.swift`. A 48-point ring of grey marks (three tiers by importance) with the N/E/S/O
 * letters standing in for the cardinal marks, and a single slender needle — read like a weather-vane —
 * pointing where the wind blows *to*, coloured by wind strength.
 *
 * This ports only the *card* variant of the Swift view (`dense: true, card: true`): the dense 48-point
 * rose and the two-halved needle, with no speed printed in the centre (the card spells the speed and
 * direction out beside the rose). The watch-complication variants of `AuraWindCircular` — the plain
 * 32-point ring, the detached arrowhead/swallowtail tips, and the big centre number — have no Android
 * surface (Aura Android is phone-only), so they aren't carried over.
 *
 * The whole thing is one [Canvas]: SwiftUI stacked rotated capsule/`Shape` views, which on Android would
 * be dozens of composables; drawing the marks by trigonometry and the needle as two rotated [Path]s is
 * the same picture for far less overhead. AEMET reports the direction the wind comes *from*, so the
 * needle's bright half points that bearing + 180°.
 */
@Composable
fun AuraWindRose(
    windSpeed: Int?,
    windDirection: WindDirection?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val needleColor = Palette.wind(windSpeed)
    Canvas(modifier = modifier) {
        val d = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        // Radius of the outer tip of every mark — the ring the marks, the cardinal letters and the
        // needle's tips all share (Swift's `markRingRadiusFactor`).
        val markRing = d * 0.485f

        // Compass points every 7.5° (the dense, nautical rose). Skip the cardinals — there the letter is
        // the mark. Three tiers: inter-cardinals longest and brightest, the 16-point marks medium, the
        // finer graduations short and dim.
        var deg = 0.0
        while (deg < 360.0) {
            if (deg % 90.0 != 0.0) {
                val tier = markTier(deg)
                drawMark(
                    center = center,
                    bearingDeg = deg,
                    ringRadius = markRing,
                    length = d * tier.length,
                    width = d * tier.width,
                    color = Color(tier.shade, tier.shade, tier.shade),
                )
            }
            deg += 7.5
        }

        // The needle, over the rose, only when a direction is known: a full-strength half aimed where the
        // wind blows toward, and the same shape dimmed for where it comes from, so the two read as one
        // continuous weather-vane through the dial.
        if (windDirection != null) {
            val towards = ((windDirection.degrees + 180.0) % 360.0).toFloat()
            val half = needleHalfPath(center, markRing, d)
            rotate(towards, pivot = center) { drawPath(half, needleColor) }
            rotate(towards + 180f, pivot = center) { drawPath(half, needleColor.copy(alpha = 0.34f)) }
        }

        // Cardinal letters — bright white, upright, on the mark ring. E and O sit a touch further out than
        // N and S so they stay readable when the needle lies over them (the Swift `rf` offsets).
        drawCardinal(textMeasurer, "N", dx = 0f, dy = -1f, rf = 0.45f, center, d)
        drawCardinal(textMeasurer, "E", dx = 1f, dy = 0f, rf = 0.47f, center, d)
        drawCardinal(textMeasurer, "S", dx = 0f, dy = 1f, rf = 0.43f, center, d)
        drawCardinal(textMeasurer, "O", dx = -1f, dy = 0f, rf = 0.45f, center, d)
    }
}

/** The size/shade tier for a mark at [deg] (lengths/widths as fractions of the diameter, shade as white). */
private data class MarkTier(val length: Float, val width: Float, val shade: Float)

private fun markTier(deg: Double): MarkTier = when {
    deg % 45.0 == 0.0 -> MarkTier(0.11f, 0.020f, 0.95f)   // inter-cardinal (NE/SE/SW/NW)
    deg % 22.5 == 0.0 -> MarkTier(0.075f, 0.016f, 0.80f)  // 16-point
    else -> MarkTier(0.05f, 0.012f, 0.62f)                // finer 7.5° graduations
}

/**
 * One radial mark at [bearingDeg] (0 = N, clockwise), drawn as a round-capped line — the Compose analogue
 * of Swift's offset capsule — from just inside the mark ring out to it. Endpoints are placed by trig so
 * no per-mark rotation layer is needed.
 */
private fun DrawScope.drawMark(
    center: Offset,
    bearingDeg: Double,
    ringRadius: Float,
    length: Float,
    width: Float,
    color: Color,
) {
    val a = Math.toRadians(bearingDeg)
    val s = sin(a).toFloat()
    val c = cos(a).toFloat()
    val outer = Offset(center.x + ringRadius * s, center.y - ringRadius * c)
    val innerR = ringRadius - length
    val inner = Offset(center.x + innerR * s, center.y - innerR * c)
    drawLine(color, inner, outer, strokeWidth = width, cap = StrokeCap.Round)
}

/**
 * One half of the needle: a long slender triangle from a small base at the dial centre to a point on the
 * mark ring, drawn pointing up (the caller rotates it onto the bearing). Widest at the centre base so the
 * two mirrored halves meet as a single tapered needle.
 */
private fun needleHalfPath(center: Offset, ringRadius: Float, d: Float): Path {
    val halfW = d * 0.05f
    return Path().apply {
        moveTo(center.x, center.y - ringRadius)   // tip at the rim
        lineTo(center.x + halfW, center.y)        // base, right of centre
        lineTo(center.x - halfW, center.y)        // base, left of centre
        close()
    }
}

/**
 * One upright cardinal letter at radius [rf]·d from the centre ([dx]/[dy] are unit offsets). Measured and
 * centred on that point so N/E/S/O share a baseline and sit in the middle of the marks around them.
 */
private fun DrawScope.drawCardinal(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    s: String,
    dx: Float,
    dy: Float,
    rf: Float,
    center: Offset,
    d: Float,
) {
    val layout = textMeasurer.measure(
        text = s,
        style = TextStyle(color = Color.White, fontSize = (d * 0.14f).toSp(), fontWeight = FontWeight.Bold),
    )
    val r = d * rf
    val pos = Offset(center.x + dx * r, center.y + dy * r)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f),
    )
}
