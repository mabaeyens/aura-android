package com.mab.aura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The lit-limb moon disc, drawn at its real phase — an ashen earthshine body always present (so a new moon
 * is a faint disc, not nothing) with the lit crescent/gibbous painted on top. Ported from the
 * `PhasedMoonDisc` `View` in `MoonPhase.swift`.
 *
 * The pure phase maths (fraction/illumination/waxing) already lives in `:core` as `MoonPhaseMath`; this is
 * purely the drawing, so it belongs in `:app`. `AuraSky` reuses [drawPhasedMoon] for the moon it paints at
 * the true lunar position, which is why the drawing lives in a shared `DrawScope` function rather than only
 * inside the composable.
 *
 * Android note: SwiftUI mirrored the whole canvas with `scaleEffect(x: -1)` to swing the lit limb to the
 * left for the waning half. Compose has no equivalent one-liner inside a shared `DrawScope`, so [waxing]
 * instead flips the sign of every x offset from the centre — the same mirror, done in the path maths.
 */

/**
 * Draw a phased moon centred at [center] with the given [radius] (in pixels). [illumination] is 0 (new) to
 * 1 (full); [waxing] puts the lit limb on the right when true, mirrored left when false. [litColor] tints
 * the lit region (a night sky reads it faintly cool).
 */
fun DrawScope.drawPhasedMoon(
    center: Offset,
    radius: Float,
    illumination: Double,
    waxing: Boolean,
    litColor: Color,
) {
    val r = radius
    val k = illumination.coerceIn(0.0, 1.0).toFloat()
    // Waxing lights the right limb; waning mirrors every x offset to the left (SwiftUI's scaleEffect x:-1).
    val sign = if (waxing) 1f else -1f

    // The dark body — earthshine, a neutral grey close to the real ashen tone, so a new moon is a
    // visible-but-unlit disc.
    drawCircle(color = Color(0.16f, 0.16f, 0.16f), radius = r, center = center)
    drawCircle(
        color = Color(0.45f, 0.45f, 0.45f).copy(alpha = 0.4f),
        radius = r,
        center = center,
        style = Stroke(width = max(0.5f, r * 0.02f)),
    )

    // The lit region: down the terminator, up the outer limb, closed.
    val n = 72
    val lit = Path()
    lit.moveTo(center.x, center.y - r)
    for (i in 0..n) {                                   // terminator, top → bottom
        val y = -r + 2 * r * i / n
        val w = sqrt(max(r * r - y * y, 0f))
        val xt = (1 - 2 * k) * w
        lit.lineTo(center.x + sign * xt, center.y + y)
    }
    for (i in n downTo 0) {                             // outer limb, bottom → top
        val y = -r + 2 * r * i / n
        val w = sqrt(max(r * r - y * y, 0f))
        lit.lineTo(center.x + sign * w, center.y + y)
    }
    lit.close()
    drawPath(lit, color = litColor)
}

/**
 * A standalone phased-moon disc for cards and detail sheets. [radius] is the disc radius; the composable
 * reserves a `radius * 3` square around it, matching the Swift frame that leaves room for the (blurred, in
 * `AuraSky`) corona. [illumination] 0…1, [waxing] lit-limb side, [litColor] the lit tint.
 */
@Composable
fun PhasedMoonDisc(
    illumination: Double,
    waxing: Boolean,
    radius: Dp,
    modifier: Modifier = Modifier,
    litColor: Color = Color(0.96f, 0.96f, 0.96f),
) {
    Canvas(modifier = modifier.size(radius * 3)) {
        val r = radius.toPx()
        drawPhasedMoon(
            center = Offset(size.width / 2f, size.height / 2f),
            radius = r,
            illumination = illumination,
            waxing = waxing,
            litColor = litColor,
        )
    }
}
