package com.mab.aura.ui.cards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mab.aura.core.time.AuraTime
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin

/**
 * Shared foundation for the two Canvas arc cards, [AuraSunArcCard] and [AuraMoonArcCard], ported from the
 * matching parts of `AuraSunArcCard`/`AuraMoonArcCard` in `AuraAppCards.swift`. Both cards draw the same
 * shallow horizon-to-horizon arc with a disc riding at its live position; only the disc (a plain sun circle
 * vs a real phased moon) and the tints differ, so the horizon + track drawing and the pure time helpers
 * live here and each card supplies its own disc on top.
 *
 * The curve is `sin(π·t)`: flat at both horizons, highest at the apex, matching `AuraSunPath`'s own altitude
 * curve so the drawn arc and the sky's sun/moon path agree.
 */

/** The arc's fixed height and the disc radius (dp). The card insets its Canvas horizontally by [ARC_GLYPH_R]
 *  so the disc sits fully inside the card at the fraction 0/1 ends instead of half-clipping the edge. */
internal const val ARC_HEIGHT = 96
internal const val ARC_GLYPH_R = 12f

/**
 * A quadratic-looking arc sampled as a 48-step polyline: `y` follows `sin(π·t)`, flat at both horizons and
 * highest at the apex. [w] is the canvas width, [baseline] the horizon `y`, [rise] the apex height. Direct
 * port of the Swift `arcPath` static.
 */
internal fun arcPath(w: Float, baseline: Float, rise: Float, from: Float, to: Float): Path {
    val p = Path()
    if (to <= from) return p
    val steps = 48
    for (i in 0..steps) {
        val t = from + (to - from) * i / steps
        val x = t * w
        val y = baseline - sin(t.toDouble() * PI).toFloat() * rise
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    return p
}

/**
 * Draw the shared part of an arc card: the dashed horizon, the faint full-day arc, and the travelled
 * portion (orto/salida -> now). [glyphR] is the disc radius in pixels; [fraction] the live 0…1 position;
 * [faintAlpha] the full-arc opacity (dimmer at night); [travelledColors] the leading->trailing gradient of
 * the travelled portion, or `null` to skip it (the night sun draws no warm trail). The disc and its glow
 * are drawn by the caller on top, since they differ between the two cards.
 */
internal fun DrawScope.drawArcTrack(
    glyphR: Float,
    fraction: Float,
    faintAlpha: Float,
    travelledColors: List<Color>?,
) {
    val w = size.width
    val baseline = size.height - 1f
    val rise = size.height - glyphR - 3f

    // Horizon: a faint dashed line along the baseline.
    drawLine(
        color = Color.White.copy(alpha = 0.18f),
        start = Offset(0f, baseline),
        end = Offset(w, baseline),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
    )

    // The full arc, faint and dashed.
    drawPath(
        path = arcPath(w, baseline, rise, 0f, 1f),
        color = Color.White.copy(alpha = faintAlpha),
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
        ),
    )

    // The travelled portion, orto/salida -> now, in a solid horizontal gradient.
    if (travelledColors != null && fraction > 0f) {
        drawPath(
            path = arcPath(w, baseline, rise, 0f, fraction),
            brush = Brush.horizontalGradient(travelledColors, startX = 0f, endX = w),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * The live disc position on the arc for [fraction], given the canvas size and disc radius. Same
 * `sin(π·t)` curve as [arcPath] so the disc rides exactly on the drawn line.
 */
internal fun DrawScope.arcPoint(glyphR: Float, fraction: Float): Offset {
    val baseline = size.height - 1f
    val rise = size.height - glyphR - 3f
    return Offset(fraction * size.width, baseline - sin(fraction.toDouble() * PI).toFloat() * rise)
}

/**
 * A soft radial-gradient glow for the disc. Android note: a `Canvas` has no cheap per-shape blur at
 * minSdk 26 (the Swift card blurred a solid circle), so the halo is a colour->transparent radial falloff
 * instead, which reads the same at this size.
 */
internal fun DrawScope.drawArcGlow(center: Offset, glyphR: Float, color: Color, alpha: Float) {
    val radius = glyphR * 2f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

// The shared wall-clock formatter now lives in :core's AuraTime (Layer D), which carries the 12/24h
// preference; this delegates so the arc cards and the Sol/Luna sheets follow the toggle once :app wires
// the setting up. Kept as an `internal` alias so the existing `hhmm(...)` call sites don't churn.
internal fun hhmm(instant: Instant): String = AuraTime.hhmm(instant)

/**
 * Compact "3 h 12 min" / "43 min". [wrapDay] adds 24 h to a negative span (so this morning's orto stands in
 * for tomorrow's). Returns null for a non-positive span. Direct port of the Swift `compact` static.
 */
internal fun compact(from: Instant, to: Instant, wrapDay: Boolean = false): String? {
    var seconds = Duration.between(from, to).seconds
    if (wrapDay && seconds < 0) seconds += 24 * 3600
    if (seconds <= 0) return null
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours h ${"%02d".format(minutes)} min" else "$minutes min"
}
