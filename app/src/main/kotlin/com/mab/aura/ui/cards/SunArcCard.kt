package com.mab.aura.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.solar.SolarTimes
import com.mab.aura.ui.sheets.AuraDetailCard
import com.mab.aura.ui.sheets.AuraSolarSheet
import com.mab.aura.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * The "Sol" card, ported from `AuraSunArcCard` in `AuraAppCards.swift`: a shallow horizon-to-horizon arc
 * with the sun riding at its live position for the hour, the orto (left) and ocaso (right) beneath it, and
 * a centre readout counting down the daylight remaining (or to tomorrow's sunrise after dark). On the phone
 * it also shows solar noon (the arc's apex) and today's daylight length with the day-over-day delta.
 *
 * Orto/ocaso come from the snapshot itself (what the rest of the app uses); the civil-twilight times and the
 * day-over-day delta are recomputed at display time from the snapshot's coordinates via [SolarTimes], and
 * simply drop when the snapshot carries no coordinates. See [ArcCard.kt][arcPath] for the shared drawing and
 * the documented Android divergences (no per-shape blur; a chevron stands in for the SF Symbol sunrise/sunset
 * glyph; 24h times until the settings store lands).
 */
@Composable
fun AuraSunArcCard(
    snapshot: WeatherSnapshot,
    size: AuraSize,
    now: Instant = Instant.now(),
    modifier: Modifier = Modifier,
) {
    val sunrise = snapshot.sunrise
    val sunset = snapshot.sunset

    AuraSection("Sol".uppercase(), size, modifier = modifier) {
        AuraDetailCard(size, sheet = { onClose -> AuraSolarSheet(snapshot, now, onClose) }) {
            if (sunrise != null && sunset != null) {
                // Today's solar solve for this location — the source of civil twilight. nil without coords.
                val solar = coordinateSolar(snapshot, now)
                val night = now.isBefore(sunrise) || now.isAfter(sunset)
                val f = sunFraction(now, sunrise, sunset)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ARC_HEIGHT.dp)
                            .padding(horizontal = ARC_GLYPH_R.dp),
                    ) {
                        val glyphR = ARC_GLYPH_R.dp.toPx()
                        drawArcTrack(
                            glyphR = glyphR,
                            fraction = f,
                            faintAlpha = if (night) 0.16f else 0.24f,
                            // The travelled portion is warm daylight; after dark there's no trail.
                            travelledColors = if (night) null else listOf(Palette.tempOrange, Palette.tempYellow),
                        )
                        // The sun: a soft glow under a solid disc, muted and pale after dark.
                        val core = if (night) Color(0.82f, 0.82f, 0.82f) else Palette.tempYellow
                        val glow = if (night) Color(0.6f, 0.6f, 0.6f) else Palette.tempOrange
                        val pos = arcPoint(glyphR, f)
                        drawArcGlow(pos, glyphR, glow, alpha = if (night) 0.35f else 0.6f)
                        drawCircle(color = core, radius = glyphR, center = pos)
                    }

                    // Orto on the left, ocaso on the right, each with its civil-twilight time beneath.
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        SunEnd(
                            icon = Icons.Filled.KeyboardArrowUp, label = "Orto", time = sunrise,
                            civilLabel = "Primera luz", civilTime = solar?.civilDawn, size = size,
                        )
                        Spacer(Modifier.weight(1f))
                        SunEnd(
                            icon = Icons.Filled.KeyboardArrowDown, label = "Ocaso", time = sunset,
                            trailing = true, civilLabel = "Última luz", civilTime = solar?.civilDusk, size = size,
                        )
                    }

                    CenteredLine(
                        text = sunReadout(now, sunrise, sunset),
                        fontSize = size.smallSize + 1,
                        alpha = 0.82f,
                    )

                    // Solar noon (the apex) and today's daylight length + delta — both from the same
                    // orto/ocaso, no new data. Dimmer than the readout, so they read as secondary.
                    solarNoon(sunrise, sunset)?.let { noon ->
                        CenteredLine("Mediodía solar ${hhmm(noon)}", size.smallSize, alpha = 0.6f)
                    }
                    dayLengthLine(snapshot, now, sunrise, sunset)?.let { line ->
                        CenteredLine(line, size.smallSize, alpha = 0.6f, maxLines = 1)
                    }
                }
            } else {
                Text(
                    text = "Horario solar no disponible",
                    fontSize = size.bodySize - 2,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }
        }
    }
}

/** Orto/ocaso end: the chevron + precise time, the label, and (with coordinates) the civil-twilight footnote. */
@Composable
private fun SunEnd(
    icon: ImageVector,
    label: String,
    time: Instant?,
    size: AuraSize,
    trailing: Boolean = false,
    civilLabel: String? = null,
    civilTime: Instant? = null,
) {
    Column(
        horizontalAlignment = if (trailing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Palette.tempOrange,
                modifier = Modifier.size((size.smallSize.value + 2).dp),
            )
            Text(
                text = time?.let(::hhmm) ?: "—",
                fontSize = size.bodySize - 2,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Text(
            text = label,
            fontSize = size.smallSize - 2,
            color = Color.White.copy(alpha = 0.6f),
        )
        // Civil twilight: the "still bright enough to be out" edge, a dim footnote under the label.
        if (civilLabel != null && civilTime != null) {
            Text(
                text = "$civilLabel ${hhmm(civilTime)}",
                fontSize = size.smallSize - 3,
                color = Color.White.copy(alpha = 0.42f),
                maxLines = 1,
            )
        }
    }
}

// --- Pure helpers, direct ports of the Swift computed properties ---

/** Today's solar solve for this location (civil twilight), or null without coordinates. */
private fun coordinateSolar(snapshot: WeatherSnapshot, now: Instant): SolarTimes? {
    val lat = snapshot.latitude ?: return null
    val lon = snapshot.longitude ?: return null
    return SolarTimes.compute(now, lat, lon)
}

/** Solar noon: the arc's apex, the midpoint between orto and ocaso; null if ocaso is not after orto. */
private fun solarNoon(sunrise: Instant, sunset: Instant): Instant? {
    if (!sunset.isAfter(sunrise)) return null
    return Instant.ofEpochMilli(sunrise.toEpochMilli() + (sunset.toEpochMilli() - sunrise.toEpochMilli()) / 2)
}

/** 0 at orto -> 1 at ocaso, clamped; pinned to the near horizon end while it's dark. */
private fun sunFraction(now: Instant, sunrise: Instant, sunset: Instant): Float {
    if (!sunset.isAfter(sunrise)) return 0.5f
    if (now.isBefore(sunrise)) return 0f
    if (now.isAfter(sunset)) return 1f
    return (Duration.between(sunrise, now).seconds.toDouble() /
        Duration.between(sunrise, sunset).seconds).toFloat()
}

/** Centre line: daylight remaining while the sun is up, else the countdown to the next sunrise. */
private fun sunReadout(now: Instant, sunrise: Instant, sunset: Instant): String {
    val isDay = !now.isBefore(sunrise) && !now.isAfter(sunset)
    if (isDay) {
        compact(now, sunset)?.let { return "Quedan $it de luz" }
    }
    // After dark the snapshot only carries today's sunrise; sun times barely move, so this morning's orto
    // stands in for tomorrow's — wrap the negative span by 24 h.
    compact(now, sunrise, wrapDay = true)?.let { return "Amanece en $it" }
    return ""
}

/**
 * The daylight-length line: total daylight, plus the day-over-day delta (a second [SolarTimes] solve of
 * yesterday from the snapshot's coordinates). The delta drops without coordinates or at a polar day/night.
 */
private fun dayLengthLine(snapshot: WeatherSnapshot, now: Instant, sunrise: Instant, sunset: Instant): String? {
    val len = compact(sunrise, sunset) ?: return null
    var line = "$len de luz"
    val delta = dayLengthDeltaMinutes(snapshot, now, sunrise, sunset)
    if (delta != null) {
        line += when {
            delta > 0 -> " · $delta min más que ayer"
            delta < 0 -> " · ${-delta} min menos que ayer"
            else -> " · igual que ayer"
        }
    }
    return line
}

/** Change in daylight length vs yesterday, whole minutes (+ lengthening, − shortening). */
private fun dayLengthDeltaMinutes(snapshot: WeatherSnapshot, now: Instant, sunrise: Instant, sunset: Instant): Int? {
    val lat = snapshot.latitude ?: return null
    val lon = snapshot.longitude ?: return null
    val today = Duration.between(sunrise, sunset).seconds
    val y = SolarTimes.compute(now.minus(Duration.ofDays(1)), lat, lon)
    val ysr = y.sunrise ?: return null
    val yss = y.sunset ?: return null
    if (!yss.isAfter(ysr)) return null
    val yesterday = Duration.between(ysr, yss).seconds
    return ((today - yesterday) / 60.0).roundToLong().toInt()
}

/** A full-width, centred secondary line, the shape every text row in this card takes. */
@Composable
private fun CenteredLine(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    alpha: Float,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = alpha),
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        modifier = Modifier.fillMaxWidth(),
    )
}
