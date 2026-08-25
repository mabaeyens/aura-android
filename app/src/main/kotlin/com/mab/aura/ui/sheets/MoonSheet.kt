package com.mab.aura.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.aura.R
import com.mab.aura.core.lunar.LunarPosition
import com.mab.aura.core.lunar.LunarTimes
import com.mab.aura.core.lunar.MoonPhaseMath
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.ui.PhasedMoonDisc
import com.mab.aura.ui.cards.hhmm
import com.mab.aura.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The detail behind the Luna card, ported from `AuraMoonSheet.swift`: the night-sky disc stays purely visual
 * on the card face; here are the numbers — tonight's phase and true illuminated fraction (from the real
 * Sun–Moon elongation, [LunarPosition]), moonrise and moonset for the location ([LunarTimes]), and the
 * countdown to the next full and new moon ([MoonPhaseMath]).
 */
@Composable
internal fun AuraMoonSheet(snapshot: WeatherSnapshot, now: Instant, onClose: () -> Unit) {
    val position = remember(now) { LunarPosition(now) }
    // The moonrise/moonset scan sweeps ~650 evaluations; solve it once per open, not per recomposition
    // (drag, etc.), matching the Luna arc card's own `remember`.
    val times = remember(snapshot.latitude, snapshot.longitude, now) {
        val lat = snapshot.latitude
        val lon = snapshot.longitude
        if (lat != null && lon != null) LunarTimes(now, lat, lon) else null
    }

    val illumPct = (position.illumination * 100).roundToInt()
    val phase = MoonPhaseMath.phaseName(position.illumination, position.waxing)

    SheetScaffold(
        gradient = listOf(Color(0.09f, 0.12f, 0.19f), Color(0.03f, 0.04f, 0.08f)),
        onClose = onClose,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Luna", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(end = 34.dp))
                Text("$phase · $illumPct % iluminada", fontSize = 15.sp, color = Color.White.copy(alpha = 0.72f))
            }

            MoonSignature(position.illumination, position.waxing)

            Column(modifier = Modifier.fillMaxWidth()) {
                FactRow("Salida", timeText(times?.moonrise)) {
                    Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = null,
                        tint = Palette.tempBlue, modifier = Modifier.size(20.dp))
                }
                FactRow("Puesta", timeText(times?.moonset)) {
                    Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null,
                        tint = Palette.tempBlue, modifier = Modifier.size(20.dp))
                }
                FactRow("Próxima llena", eventText(MoonPhaseMath.nextFullMoon(now), now)) {
                    Icon(painterResource(R.drawable.ic_wx_clear_night), contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(20.dp))
                }
                FactRow("Próxima nueva", eventText(MoonPhaseMath.nextNewMoon(now), now), last = true) {
                    Icon(painterResource(R.drawable.ic_wx_clear_night), contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
                }
            }

            Text(
                text = "La fase y el porcentaje se calculan a partir de la posición real del Sol y la Luna; " +
                    "salida y puesta, para tu ubicación. Las horas de las próximas fases son aproximadas " +
                    "(unas horas de margen).",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The signature: tonight's real phase, large, over a soft cool glow (a radial falloff — no per-shape blur
 *  at minSdk 26). The glow fades with the illuminated fraction, matching the Swift. */
@Composable
private fun MoonSignature(illumination: Double, waxing: Boolean) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0.66f, 0.72f, 0.92f, (0.35f * illumination).toFloat()),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = 130.dp.toPx(),
                ),
                radius = 130.dp.toPx(),
                center = c,
            )
        }
        PhasedMoonDisc(
            illumination = illumination,
            waxing = waxing,
            radius = 52.dp,
            litColor = Color(0.94f, 0.96f, 1.0f),
        )
    }
}

private fun timeText(instant: Instant?): String = instant?.let { hhmm(it) } ?: "—"

private val monthDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES")).withZone(ZoneId.systemDefault())

/** "28 ago · en 5 días" — the date plus a relative-day tail (hoy / mañana / en N días). */
private fun eventText(date: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(
        now.atZone(zone).toLocalDate(),
        date.atZone(zone).toLocalDate(),
    ).toInt()
    val tail = when {
        days <= 0 -> "hoy"
        days == 1 -> "mañana"
        else -> "en $days días"
    }
    return "${monthDayFormatter.format(date)} · $tail"
}
