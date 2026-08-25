package com.mab.aura.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mab.aura.core.model.UVHourSlot
import com.mab.aura.core.model.UVNow
import com.mab.aura.core.model.current
import com.mab.aura.core.model.todaySlots
import com.mab.aura.core.uv.UVIndex
import com.mab.aura.ui.sheets.AuraDetailCard
import com.mab.aura.ui.sheets.AuraUVSheet
import com.mab.aura.ui.theme.Palette
import java.time.Instant
import kotlin.math.max

/**
 * The UV-index card, ported from `AuraUVCard` + `UVHourStrip` in `AuraAppCards.swift`: AEMET's forecast
 * daily-max UV in its WHO band colour (a swatch labelled "Máx. hoy"), the band name, a one-line protection
 * cue, and — when CAMS hourly data is present — a slim band-tinted bar strip showing how the UV actually
 * rises and falls through today, with the live "Ahora" value and the protection window called out above.
 *
 * One Android divergence from the Swift, consistent with the deferred `UVIndex.glyph` in `:core`: the
 * Swift card drew a per-band protection glyph (an SF Symbol escalation, sun → sunglasses → umbrella)
 * beside the band name. The Material *core* icon set carries no such weather glyphs, and the UV
 * complication that glyph taught doesn't exist on Android yet, so it's left out here — the coloured swatch
 * and band name already carry the level. It returns with the icon-set work if a UV widget lands.
 *
 * The scalar readouts above the strip (the live "Ahora" value, today's peak, the protection window) come
 * from [UVNow] in `:core`, so that arithmetic is unit-tested rather than living in the view.
 */
@Composable
fun AuraUVCard(
    uvIndex: UVIndex,
    size: AuraSize,
    modifier: Modifier = Modifier,
    hourly: List<UVHourSlot> = emptyList(),
    now: Instant = Instant.now(),
    cloudy: Boolean = false,
) {
    val color = Palette.uvIndex(uvIndex.value)
    // Today's daytime UV hours from CAMS — the per-hour granularity AEMET's daily-max lacks.
    val today = hourly.todaySlots(now).filter { it.uv > 0 }

    AuraSection("Índice UV".uppercase(), size, modifier = modifier) {
        AuraDetailCard(size, sheet = { onClose -> AuraUVSheet(uvIndex, cloudy, onClose) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(size.stackSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The swatch is AEMET's clear-sky daily maximum, not the current UV — labelled so on the
                    // card face, so a big "5" on a rainy afternoon reads as "today's peak", not "right now".
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(CircleShape).background(color),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${uvIndex.value}",
                                fontSize = size.bodySize + 3,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                            )
                        }
                        Text(
                            text = "Máx. hoy",
                            fontSize = size.smallSize - 2,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = uvIndex.bandName,
                            fontSize = size.bodySize - 1,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                        )
                        Text(
                            text = uvIndex.advice,
                            fontSize = size.smallSize - 1,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                        )
                    }
                }

                // The hourly curve, only when CAMS data is present. The swatch above is AEMET's forecast
                // daily maximum; this is how the UV actually moves through today, hour by hour.
                if (today.size >= 3) {
                    UVHourStrip(
                        today = today,
                        nowSlot = hourly.current(now),
                        now = now,
                        size = size,
                        cloudy = cloudy,
                    )
                }
            }
        }
    }
}

/**
 * Today's UV, hour by hour, as a slim band-tinted bar strip under the daily-max swatch. Bar heights track
 * the day's own shape (scaled to today's peak, so a low-UV winter day still reads); colour carries the
 * absolute WHO band. The current hour is outlined and its value called out above.
 */
@Composable
private fun UVHourStrip(
    today: List<UVHourSlot>,
    nowSlot: UVHourSlot?,
    now: Instant,
    size: AuraSize,
    cloudy: Boolean,
) {
    val readout = UVNow.from(today, now)
    val peak = today.maxByOrNull { it.uv }
    val scale = max(peak?.uv ?: 1.0, 1.0)
    val barsH = 34.dp

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // A compact readout from the same series: the honest live value now (unlike the daily max) and
        // today's peak with its hour. The cloud cue marks a cloud-attenuated "Ahora" reading.
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (nowSlot != null && nowSlot.uv > 0) {
                val band = UVIndex(nowSlot.index).bandName.lowercase()
                val cloud = if (cloudy) "☁ " else ""
                Text(
                    text = "${cloud}Ahora ${nowSlot.index} ($band)",
                    fontSize = size.smallSize - 2,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                )
                Text("·", fontSize = size.smallSize - 2, color = Color.White.copy(alpha = 0.4f))
            }
            readout.peakIndex?.let { peakIdx ->
                Text(
                    text = "máx $peakIdx a las ${readout.peakHour}h",
                    fontSize = size.smallSize - 2,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }

        // The actionable window from the same series: when to actually protect yourself, i.e. the stretch
        // where the index sits at or above the WHO threshold of 3.
        readout.protection?.let { w ->
            Text(
                text = "Protégete de ${w.first}h a ${w.last}h",
                fontSize = size.smallSize - 2,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }

        Row(
            modifier = Modifier.height(barsH).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            today.forEach { slot ->
                val isNow = slot.date == nowSlot?.date
                UVBar(slot = slot, scale = scale, barsH = barsH, isNow = isNow, anyNow = nowSlot != null)
            }
        }
    }
}

/** One hour's bar: height scaled to today's peak, tinted by its WHO band, outlined when it's the live hour. */
@Composable
private fun RowScope.UVBar(
    slot: UVHourSlot,
    scale: Double,
    barsH: androidx.compose.ui.unit.Dp,
    isNow: Boolean,
    anyNow: Boolean,
) {
    val shape = RoundedCornerShape(2.dp)
    val h = max((barsH.value * (slot.uv / scale)).toFloat(), 3f).dp
    Box(
        modifier = Modifier
            .weight(1f)
            .height(h)
            // Dim the non-current hours only when there *is* a current hour to contrast against.
            .alpha(if (isNow || !anyNow) 1f else 0.72f)
            .clip(shape)
            .background(Palette.uvIndex(slot.index))
            .then(if (isNow) Modifier.border(1.5.dp, Color.White, shape) else Modifier),
    )
}
