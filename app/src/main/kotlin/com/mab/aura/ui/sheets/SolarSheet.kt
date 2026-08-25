package com.mab.aura.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.aura.R
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.solar.SolarTimes
import com.mab.aura.ui.cards.hhmm
import java.time.Duration
import java.time.Instant

/**
 * The detail behind the Sol card, ported from `AuraSolarSheet.swift`: the daytime twin of [AuraMoonSheet].
 * The drawn day arc stays purely visual on the card face; here are the numbers — civil twilight (first and
 * last light), orto and ocaso, solar noon, and today's daylight length with the day-over-day delta.
 *
 * Orto/ocaso come from the snapshot (what the rest of the app uses); the twilight times, solar noon and
 * length are recomputed from the snapshot's coordinates ([SolarTimes]), no new data.
 */
@Composable
internal fun AuraSolarSheet(snapshot: WeatherSnapshot, now: Instant, onClose: () -> Unit) {
    val sunrise = snapshot.sunrise
    val sunset = snapshot.sunset
    val solar = coordinateSolar(snapshot, now)
    val noon = solarNoon(sunrise, sunset)
    val dayLength = dayLength(sunrise, sunset)

    SheetScaffold(
        gradient = listOf(Color(0.13f, 0.15f, 0.24f), Color(0.04f, 0.05f, 0.09f)),
        onClose = onClose,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sol", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(end = 34.dp))
                Text(subtitle(snapshot, now, dayLength), fontSize = 15.sp, color = Color.White.copy(alpha = 0.72f))
            }

            SunSignature()

            Column(modifier = Modifier.fillMaxWidth()) {
                FactRow("Primera luz", timeText(solar?.civilDawn)) {
                    GlyphMark(R.drawable.ic_wx_sunrise)
                }
                FactRow("Orto", timeText(sunrise)) {
                    GlyphMark(R.drawable.ic_wx_sunrise)
                }
                FactRow("Mediodía solar", timeText(noon)) {
                    GlyphMark(R.drawable.ic_wx_clear_day)
                }
                FactRow("Ocaso", timeText(sunset)) {
                    GlyphMark(R.drawable.ic_wx_sunset)
                }
                FactRow("Última luz", timeText(solar?.civilDusk)) {
                    GlyphMark(R.drawable.ic_wx_sunset)
                }
                FactRow("Luz del día", dayLength?.let { durationText(it.seconds) } ?: "—", last = true) {
                    DotMark(Color.White.copy(alpha = 0.85f))
                }
            }

            Text(
                text = "Orto y ocaso vienen del parte de AEMET para tu municipio; la primera y la última luz " +
                    "(crepúsculo civil), el mediodía solar y la duración del día se calculan para tus coordenadas.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The signature: a warm sun over a soft glow — the daytime echo of the moon disc. Android note: no per-shape
 *  blur at minSdk 26, so the glow is a radial falloff and the sun a plain warm disc (as on the Sol arc card),
 *  standing in for the Swift `sun.max.fill` symbol Material core does not carry. */
@Composable
private fun SunSignature() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(1f, 0.82f, 0.4f, 0.4f), Color.Transparent),
                    center = c,
                    radius = 130.dp.toPx(),
                ),
                radius = 130.dp.toPx(),
                center = c,
            )
            drawCircle(color = Color(1f, 0.9f, 0.55f), radius = 38.dp.toPx(), center = c)
        }
    }
}

/** A colourful Meteocons glyph (sunrise/sunset/sun) as a fact-row mark — the counterpart to iOS's sun symbols.
 *  Drawn untinted so it keeps its own warm colours, like the sky glyphs on the cards. */
@Composable
private fun GlyphMark(@DrawableRes icon: Int) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun DotMark(tint: Color) {
    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(tint))
}

private fun coordinateSolar(snapshot: WeatherSnapshot, now: Instant): SolarTimes? {
    val lat = snapshot.latitude ?: return null
    val lon = snapshot.longitude ?: return null
    return SolarTimes.compute(now, lat, lon)
}

/** Solar noon: the midpoint between orto and ocaso, the arc's apex; null if ocaso is not after orto. */
private fun solarNoon(sunrise: Instant?, sunset: Instant?): Instant? {
    if (sunrise == null || sunset == null || !sunset.isAfter(sunrise)) return null
    return Instant.ofEpochMilli(sunrise.toEpochMilli() + (sunset.toEpochMilli() - sunrise.toEpochMilli()) / 2)
}

/** Today's daylight length, orto → ocaso; null if ocaso is not after orto. */
private fun dayLength(sunrise: Instant?, sunset: Instant?): Duration? {
    if (sunrise == null || sunset == null || !sunset.isAfter(sunrise)) return null
    return Duration.between(sunrise, sunset)
}

/** The headline fact: today's daylight length plus the day-over-day delta. */
private fun subtitle(snapshot: WeatherSnapshot, now: Instant, dayLength: Duration?): String {
    val len = dayLength ?: return "Horario solar no disponible"
    var s = "${durationText(len.seconds)} de luz"
    dayLengthDeltaMinutes(snapshot, now, len)?.let { dm ->
        s += when {
            dm > 0 -> " · $dm min más que ayer"
            dm < 0 -> " · ${-dm} min menos que ayer"
            else -> " · igual que ayer"
        }
    }
    return s
}

/** Change in daylight length vs yesterday, whole minutes (+ lengthening, − shortening). Null without
 *  coordinates or at a polar day/night, matching the Swift. */
private fun dayLengthDeltaMinutes(snapshot: WeatherSnapshot, now: Instant, today: Duration): Int? {
    val lat = snapshot.latitude ?: return null
    val lon = snapshot.longitude ?: return null
    val y = SolarTimes.compute(now.minus(Duration.ofDays(1)), lat, lon)
    val ysr = y.sunrise ?: return null
    val yss = y.sunset ?: return null
    if (!yss.isAfter(ysr)) return null
    val yLen = Duration.between(ysr, yss).seconds
    return Math.round((today.seconds - yLen) / 60.0).toInt()
}

private fun timeText(instant: Instant?): String = instant?.let { hhmm(it) } ?: "—"

/** Compact "13 h 24 min" / "43 min", matching the Sol card's own daylight-length line. */
private fun durationText(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours h ${"%02d".format(minutes)} min" else "$minutes min"
}
