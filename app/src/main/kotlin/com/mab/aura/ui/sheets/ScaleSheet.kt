package com.mab.aura.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.aura.core.air.AirComponent
import com.mab.aura.core.air.AirQuality
import com.mab.aura.ui.cards.relative
import com.mab.aura.ui.theme.Palette
import java.time.Instant
import java.util.Locale

/**
 * The shared scaffold for the three reference-scale sheets (Beaufort, ICA, UV), ported from the private
 * `AuraScaleSheet` / `AuraScaleBar` / `AuraScaleRow` / `AirComponentScale` in `AuraScaleSheets.swift`.
 *
 * Each sheet is a title, a "where you are now" subtitle, the signature colour ramp with a marker at the
 * current reading, the coloured level rows, and a small footnote. Dark, over a night-sky gradient, to match
 * the app. It fills the [ModalBottomSheet] the tap affordance ([AuraDetail]) presents it in.
 *
 * Android note: the Swift `scrolls` escape hatch (for the offline `ImageRenderer`) is dropped — Android has
 * no such offline render tool, so the content simply scrolls. The sheet's own corner close button remains,
 * on top of the drag handle Material already draws.
 */
@Composable
internal fun AuraScaleSheet(
    title: String,
    subtitle: String,
    footnote: String,
    barColors: List<Color>,
    markerFraction: Double?,
    markerLabel: String,
    onClose: () -> Unit,
    // The optional live cue under the subtitle (the UV sheet's cloud note). The Swift paired an SF Symbol
    // with the text; Material core has no cloud glyph, so — as on the UV card — a "☁" prefix stands in.
    note: String? = null,
    rows: @Composable ColumnScope.() -> Unit,
) {
    SheetScaffold(
        gradient = listOf(Color(0.09f, 0.12f, 0.19f), Color(0.03f, 0.04f, 0.08f)),
        onClose = onClose,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(end = 34.dp), // clear of the close button
                )
                Text(text = subtitle, fontSize = 15.sp, color = Color.White.copy(alpha = 0.72f))
                if (note != null) {
                    Text(
                        text = "☁  $note",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            AuraScaleBar(barColors, markerFraction, markerLabel)

            rows()

            Text(
                text = footnote,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The dark gradient body, corner close button and 20 dp inset shared by every sheet (the scale sheets and
 * the Sol/Luna sheets alike). Ports the `ZStack { gradient; content }.overlay(closeButton)` chrome.
 */
@Composable
internal fun SheetScaffold(
    gradient: List<Color>,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(gradient)),
    ) {
        // Scrolls within the bottom sheet: the ICA sheet in particular (six levels + five pollutant scales)
        // is taller than a sheet detent. The close button below is a sibling of this Column, so it stays put.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            content = content,
        )
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cerrar",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(27.dp),
            )
        }
    }
}

/**
 * One fact row for the Sol/Luna sheets: a tinted leading mark, a label, and a right-aligned value, with a
 * hairline divider beneath every row but the last. Ports the private `factRow` shared by `AuraSolarSheet`
 * and `AuraMoonSheet`. The [mark] is a slot so each row supplies its own glyph (a chevron for a rise/set, a
 * reused weather drawable for the sun/moon marks — Material core has no sunrise/sunset/moon glyph).
 */
@Composable
internal fun FactRow(label: String, value: String, last: Boolean = false, mark: @Composable () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) { mark() }
            Text(text = label, fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        if (!last) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        }
    }
}

/**
 * The scale itself: a continuous colour ramp — low on the left, high on the right — with a marker riding it
 * at the current reading. Aura's own idiom (the temperature strip and the wind vane read the same way). The
 * marker is a value bubble over a downward pointer; it hides when there is no current reading.
 */
@Composable
private fun AuraScaleBar(colors: List<Color>, markerFraction: Double?, label: String) {
    val barHeight = 16.dp
    val markerWidth = 74.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(vertical = 8.dp),
    ) {
        val w = maxWidth
        // The ramp, pinned to the bottom of the frame so the marker's pointer rests on it from above.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(colors))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50)),
        )
        markerFraction?.let { raw ->
            val f = raw.coerceIn(0.0, 1.0)
            val x = (w * f.toFloat() - markerWidth / 2).coerceIn(0.dp, w - markerWidth)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = x)
                    .width(markerWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.85f),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
                Box(
                    modifier = Modifier
                        .size(width = 12.dp, height = 8.dp)
                        .clip(DownTriangle)
                        .background(Color.White),
                )
            }
        }
    }
}

/** A small triangle pointing down, for the scale-bar marker. */
private val DownTriangle: Shape = object : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline = Outline.Generic(
        Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        },
    )
}

/**
 * One level of a scale: a colour swatch carrying its short label, the level name (with a "current" pill when
 * it is the reading in effect), and a line of what it means. The current row is ringed and lifted.
 * [currentLabel] is the pill text — "Ahora" for live readings, "Máx. hoy" on the UV sheet (AEMET's UV is a
 * daily-max forecast, not a live value).
 */
@Composable
internal fun AuraScaleRow(
    color: Color,
    badge: String,
    name: String,
    detail: String,
    isCurrent: Boolean,
    currentLabel: String = "Ahora",
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (isCurrent) 0.10f else 0f))
            .border(2.dp, color.copy(alpha = if (isCurrent) 0.9f else 0f), shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = badge,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                if (isCurrent) CurrentPill(currentLabel)
            }
            Text(text = detail, fontSize = 14.sp, color = Color.White.copy(alpha = 0.72f))
        }
    }
}

/** The little white "Ahora" / "Máx. hoy" / "dominante" capsule with black text used across the rows. */
@Composable
internal fun CurrentPill(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = Color.Black.copy(alpha = 0.85f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * One pollutant on its own 1…6 ICA ramp: the label, the value (or "No medido en esta estación"), a slim
 * colour ramp with a marker where the value falls, and the band name with its source line. Unmeasured
 * pollutants show a flat grey rail with no marker — MITECO's grey-for-unavailable convention, as on the card.
 */
@Composable
internal fun AirComponentScale(token: String, component: AirComponent?, isDriver: Boolean, now: Instant) {
    val measured = component != null
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = AirComponent.label(token),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = if (measured) 1f else 0.4f),
            )
            if (isDriver && measured) CurrentPill("dominante")
            Spacer(modifier = Modifier.weight(1f))
            if (component != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(component.valueText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(" µg/m³", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            } else {
                Text(
                    text = "No medido en esta estación",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.42f),
                )
            }
        }

        ComponentRamp(component)

        if (component != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = AirQuality.categoryName(component.icaCategory),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.airQuality(component.icaCategory),
                )
                componentSource(component, now)?.let { source ->
                    Text("·", color = Color.White.copy(alpha = 0.3f))
                    Text(source, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
                }
            }
        }
    }
}

/** The 1…6 ICA ramp with a circle marker at the pollutant's fraction, or a flat grey rail when unmeasured. */
@Composable
private fun ComponentRamp(component: AirComponent?) {
    if (component == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0.5f, 0.5f, 0.5f, 0.26f))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)),
        )
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(16.dp)) {
        val w = maxWidth
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient((1..6).map { Palette.airQuality(it) })),
        )
        val x = (w * component.icaFraction.toFloat() - 8.dp).coerceIn(0.dp, w - 16.dp)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = x)
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.airQuality(component.icaCategory))
                .border(2.5.dp, Color.White, RoundedCornerShape(50)),
        )
    }
}

/**
 * "Retiro · a 1,7 km · hace 1 h" — the station this pollutant came from, its distance, and how fresh the
 * reading is. Different pollutants can show different stations and times; that is the point.
 */
private fun componentSource(c: AirComponent, now: Instant): String? {
    val station = c.station ?: return null
    val parts = mutableListOf(station)
    c.distanceKm?.let { km ->
        parts += if (km < 10) {
            // Force a dot from the format (locale-independent), then swap to the Spanish decimal comma,
            // matching the Swift `String(format:).replacingOccurrences`.
            "a " + String.format(Locale.US, "%.1f", km).replace(".", ",") + " km"
        } else {
            "a ${Math.round(km)} km"
        }
    }
    c.measured?.let { parts += relative(it, now) }
    return parts.joinToString(" · ")
}
