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
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.aura.R
import com.mab.aura.core.lunar.LunarPosition
import com.mab.aura.core.lunar.LunarTimes
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.ui.drawPhasedMoon
import com.mab.aura.ui.sheets.AuraDetailCard
import com.mab.aura.ui.sheets.AuraMoonSheet
import com.mab.aura.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/**
 * The "Luna" card, the night twin of [AuraSunArcCard], ported from `AuraMoonArcCard` in `AuraAppCards.swift`.
 * It traces the moon's *own* path, salida (moonrise, east/left) -> puesta (moonset, west/right), with the moon
 * riding at its live position and wearing tonight's true phase — the very disc [drawPhasedMoon] paints in
 * `AuraSky`. When the moon is below the horizon it rests at the eastern end, waiting to rise, and the centre
 * counts down to the next of salida/puesta.
 *
 * The moon's appearance and phase come from `:core`'s [LunarTimes]/[LunarPosition], solved once (the
 * `LunarTimes` scan is ~650 position evaluations, so it's [remember]ed rather than repeated per
 * recomposition). Falls back to "unavailable" when the snapshot carries no coordinates. See
 * [ArcCard.kt][arcPath] for the shared drawing and the documented Android divergences.
 */
@Composable
fun AuraMoonArcCard(
    snapshot: WeatherSnapshot,
    size: AuraSize,
    now: Instant = Instant.now(),
    modifier: Modifier = Modifier,
) {
    // Solve the moon's real appearance and phase once for this place and hour.
    val moon = remember(snapshot.latitude, snapshot.longitude, now) { MoonState.solve(snapshot, now) }

    AuraSection("Luna".uppercase(), size, modifier = modifier) {
        AuraDetailCard(size, sheet = { onClose -> AuraMoonSheet(snapshot, now, onClose) }) {
            val moonrise = moon.moonrise
            val moonset = moon.moonset
            if (moonrise != null && moonset != null) {
                val isUp = !moonrise.isAfter(now) && moonset.isAfter(now)
                val f = moonFraction(now, moonrise, moonset, isUp)

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
                            faintAlpha = 0.24f,
                            // The travelled portion is cool moonlight; drawn only while the moon is up (f > 0).
                            travelledColors = listOf(Palette.tempBlue, Color(0.95f, 0.95f, 0.95f)),
                        )
                        // The moon: a soft cool glow — dimming toward a new moon — under tonight's real phase.
                        val pos = arcPoint(glyphR, f)
                        drawArcGlow(
                            center = pos,
                            glyphR = glyphR,
                            color = Color(0.66f, 0.72f, 0.92f),
                            alpha = 0.45f * max(moon.illumination, 0.22).toFloat(),
                        )
                        drawPhasedMoon(
                            center = pos,
                            radius = glyphR,
                            illumination = moon.illumination,
                            waxing = moon.waxing,
                            litColor = Color(0.94f, 0.96f, 1.0f),
                        )
                    }

                    // Salida on the left (moonrise, east), puesta on the right (moonset, west).
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        MoonEnd(R.drawable.ic_arrow_up, "Salida", moonrise, size)
                        Spacer(Modifier.weight(1f))
                        MoonEnd(R.drawable.ic_arrow_down, "Puesta", moonset, size, trailing = true)
                    }

                    Text(
                        text = moonReadout(now, moonrise, moonset, isUp),
                        fontSize = size.smallSize + 1,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    text = "Horario lunar no disponible",
                    fontSize = size.bodySize - 2,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }
        }
    }
}

/** Salida/puesta end: the arrow in cool moonlight + the precise time, over its label. */
@Composable
private fun MoonEnd(
    @DrawableRes icon: Int,
    label: String,
    time: Instant?,
    size: AuraSize,
    trailing: Boolean = false,
) {
    Column(
        horizontalAlignment = if (trailing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The moon arc mirrors iOS's plain arrow.up/arrow.down (not the sun arc's colour glyph): a
            // monochrome straight arrow tinted in cool moonlight.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Palette.tempBlue,
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
    }
}

// --- Pure helpers ---

/** The moon's appearance and phase for a place and hour, solved once (see [MoonState.solve]). */
private data class MoonState(
    val moonrise: Instant?,
    val moonset: Instant?,
    val illumination: Double,
    val waxing: Boolean,
) {
    companion object {
        fun solve(snapshot: WeatherSnapshot, now: Instant): MoonState {
            val lat = snapshot.latitude
            val lon = snapshot.longitude
            val times = if (lat != null && lon != null) LunarTimes(now, lat, lon) else null
            val pos = LunarPosition(now)
            return MoonState(times?.moonrise, times?.moonset, pos.illumination, pos.waxing)
        }
    }
}

/** 0 at salida -> 1 at puesta while the moon is up; rests at 0 (the eastern horizon) when it is down. */
private fun moonFraction(now: Instant, moonrise: Instant, moonset: Instant, isUp: Boolean): Float {
    if (!isUp || !moonset.isAfter(moonrise)) return 0f
    val f = Duration.between(moonrise, now).seconds.toDouble() / Duration.between(moonrise, moonset).seconds
    return f.coerceIn(0.0, 1.0).toFloat()
}

/** Centre line: the countdown to the moon's next event — its puesta while up, otherwise its salida. */
private fun moonReadout(now: Instant, moonrise: Instant, moonset: Instant, isUp: Boolean): String {
    if (isUp) {
        compact(now, moonset)?.let { return "Se pone en $it" }
    } else {
        compact(now, moonrise)?.let { return "Sale en $it" }
    }
    return ""
}
