package com.mab.aura.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.aura.R

// The AEMET colour convention for front symbols, reused by the glyphs and (loosely) the letter marks.
private val WarmRed = Color(red = 0.85f, green = 0.22f, blue = 0.22f)
private val ColdBlue = Color(red = 0.22f, green = 0.38f, blue = 0.82f)
private val OccludedPurple = Color(red = 0.56f, green = 0.30f, blue = 0.72f)
private val TroughAmber = Color(red = 0.85f, green = 0.55f, blue = 0.20f)
private val IsobarGrey = Color.White.copy(alpha = 0.55f)

/** Which front (or line) a legend glyph draws. */
private enum class FrontGlyph { ISOBARS, WARM, COLD, OCCLUDED, STATIONARY, TROUGH }

/**
 * The surface-analysis symbols legend, presented from [com.mab.aura.ui.cards.AuraSurfaceCard]'s info button.
 * The map image itself is data and stays as AEMET ships it (Spanish A = alta = high, B = baja = low); this
 * sheet is the localized key that names every mark on it.
 *
 * Unlike the colour-ramp scale sheets, this is a plain list of rows, each a small self-drawn glyph plus a
 * label and one line of meaning. The front glyphs are drawn with [Canvas] (as the app draws its own visuals
 * everywhere) so they match in both the ES and EN builds; a footnote credits AEMET as the source of truth.
 */
@Composable
internal fun AuraSurfaceSheet(onClose: () -> Unit) {
    SheetScaffold(
        gradient = listOf(Color(0.09f, 0.12f, 0.19f), Color(0.03f, 0.04f, 0.08f)),
        onClose = onClose,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.sheet_surface_title),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(end = 34.dp), // clear of the close button
                )
                Text(
                    text = stringResource(R.string.sheet_surface_subtitle),
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            LegendRow(
                label = stringResource(R.string.sheet_surface_isobars),
                detail = stringResource(R.string.sheet_surface_isobars_detail),
            ) { GlyphCanvas(FrontGlyph.ISOBARS) }
            LegendRow(
                label = stringResource(R.string.sheet_surface_high),
                detail = stringResource(R.string.sheet_surface_high_detail),
            ) { LetterMark("A", ColdBlue) }
            LegendRow(
                label = stringResource(R.string.sheet_surface_low),
                detail = stringResource(R.string.sheet_surface_low_detail),
            ) { LetterMark("B", WarmRed) }
            LegendRow(
                label = stringResource(R.string.sheet_surface_warm),
                detail = stringResource(R.string.sheet_surface_warm_detail),
            ) { GlyphCanvas(FrontGlyph.WARM) }
            LegendRow(
                label = stringResource(R.string.sheet_surface_cold),
                detail = stringResource(R.string.sheet_surface_cold_detail),
            ) { GlyphCanvas(FrontGlyph.COLD) }
            LegendRow(
                label = stringResource(R.string.sheet_surface_occluded),
                detail = stringResource(R.string.sheet_surface_occluded_detail),
            ) { GlyphCanvas(FrontGlyph.OCCLUDED) }
            LegendRow(
                label = stringResource(R.string.sheet_surface_stationary),
                detail = stringResource(R.string.sheet_surface_stationary_detail),
            ) { GlyphCanvas(FrontGlyph.STATIONARY) }
            LegendRow(
                label = stringResource(R.string.sheet_surface_trough),
                detail = stringResource(R.string.sheet_surface_trough_detail),
                last = true,
            ) { GlyphCanvas(FrontGlyph.TROUGH) }

            Text(
                text = stringResource(R.string.sheet_surface_footnote),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** One legend row: a fixed-width glyph, then the symbol name and a line of what it means, over a divider. */
@Composable
private fun LegendRow(
    label: String,
    detail: String,
    last: Boolean = false,
    glyph: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) { glyph() }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(text = detail, fontSize = 13.sp, color = Color.White.copy(alpha = 0.68f))
            }
        }
        if (!last) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        }
    }
}

/** The high/low pressure marks: AEMET's letter (A = alta, B = baja) in its colour, as on the map. */
@Composable
private fun LetterMark(letter: String, color: Color) {
    Text(text = letter, fontSize = 26.sp, fontWeight = FontWeight.Black, color = color)
}

/** A 60×26 dp glyph for one front/line type, drawn to match AEMET's map symbols. */
@Composable
private fun GlyphCanvas(glyph: FrontGlyph) {
    Canvas(modifier = Modifier.size(width = 60.dp, height = 26.dp)) {
        when (glyph) {
            FrontGlyph.ISOBARS -> drawIsobars()
            FrontGlyph.WARM -> drawWarmFront()
            FrontGlyph.COLD -> drawColdFront()
            FrontGlyph.OCCLUDED -> drawOccludedFront()
            FrontGlyph.STATIONARY -> drawStationaryFront()
            FrontGlyph.TROUGH -> drawTrough()
        }
    }
}

// --- Glyph drawing. Each draws a horizontal front line across the canvas with its symbols riding it. ---

/** Three thin parallel lines: what isobars look like on the map (lines of equal pressure). */
private fun DrawScope.drawIsobars() {
    val stroke = 1.5.dp.toPx()
    listOf(0.30f, 0.50f, 0.70f).forEach { f ->
        val y = size.height * f
        drawLine(IsobarGrey, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
    }
}

/** A red line with filled semicircle bumps on top: a warm front (warm air advancing). */
private fun DrawScope.drawWarmFront() {
    val cy = size.height * 0.62f
    drawLine(WarmRed, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2.dp.toPx())
    symbolCentres(3).forEach { x -> semicircle(x, cy, up = true, WarmRed) }
}

/** A blue line with filled triangle spikes on top: a cold front (cold air advancing). */
private fun DrawScope.drawColdFront() {
    val cy = size.height * 0.62f
    drawLine(ColdBlue, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2.dp.toPx())
    symbolCentres(3).forEach { x -> triangle(x, cy, up = true, ColdBlue) }
}

/** A purple line alternating a triangle and a semicircle: an occluded front (a cold front overtaking a warm). */
private fun DrawScope.drawOccludedFront() {
    val cy = size.height * 0.62f
    drawLine(OccludedPurple, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2.dp.toPx())
    symbolCentres(4).forEachIndexed { i, x ->
        if (i % 2 == 0) triangle(x, cy, up = true, OccludedPurple) else semicircle(x, cy, up = true, OccludedPurple)
    }
}

/** A line with blue triangles above and red semicircles below, alternating: a stationary front (neither wins). */
private fun DrawScope.drawStationaryFront() {
    val cy = size.height * 0.5f
    drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2.dp.toPx())
    symbolCentres(4).forEachIndexed { i, x ->
        if (i % 2 == 0) triangle(x, cy, up = true, ColdBlue) else semicircle(x, cy, up = false, WarmRed)
    }
}

/** A dashed amber line: a trough or instability line (a pressure trough, no temperature boundary). */
private fun DrawScope.drawTrough() {
    val cy = size.height * 0.5f
    drawLine(
        color = TroughAmber,
        start = Offset(0f, cy),
        end = Offset(size.width, cy),
        strokeWidth = 2.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f)),
    )
}

/** Evenly spaced symbol x-positions across the canvas width, inset so nothing clips the edges. */
private fun DrawScope.symbolCentres(count: Int): List<Float> {
    val inset = size.width * 0.12f
    val span = size.width - 2 * inset
    return (0 until count).map { inset + span * (it + 0.5f) / count }
}

/** The symbol half-height/radius: bumps and spikes sit this far off the line. */
private fun DrawScope.symbolSize(): Float = size.height * 0.30f

/** A filled half-disc riding the line at [x], bulging [up] or down. */
private fun DrawScope.semicircle(x: Float, cy: Float, up: Boolean, color: Color) {
    val r = symbolSize()
    // startAngle 180° sweeping 180° passes through straight-up (the top half); 0° sweeping 180° gives the
    // bottom half. useCenter fills it to a half-disc resting on the line.
    drawArc(
        color = color,
        startAngle = if (up) 180f else 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(x - r, cy - r),
        size = Size(2 * r, 2 * r),
    )
}

/** A filled triangle riding the line at [x], apex pointing [up] or down. */
private fun DrawScope.triangle(x: Float, cy: Float, up: Boolean, color: Color) {
    val r = symbolSize()
    val apexY = if (up) cy - r else cy + r
    val path = Path().apply {
        moveTo(x - r, cy)
        lineTo(x + r, cy)
        lineTo(x, apexY)
        close()
    }
    drawPath(path, color)
}
