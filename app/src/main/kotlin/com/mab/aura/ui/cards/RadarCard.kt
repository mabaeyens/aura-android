package com.mab.aura.ui.cards

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mab.aura.R
import java.time.Duration
import java.time.Instant

/**
 * A fetched radar frame plus the context the card shows around it, ported from `AuraRadarInfo` in
 * `AuraAppCards.swift`. Not stored in `WeatherSnapshot` (its image bytes would bloat the cached snapshot)
 * — the app fetches and passes it separately. The Swift `Image` becomes a decoded Compose [ImageBitmap].
 */
data class AuraRadarInfo(
    val image: ImageBitmap,
    /** Radar site name, e.g. "Madrid". */
    val siteName: String,
    /** When the frame was fetched, for the freshness label. */
    val time: Instant,
)

/**
 * The nearest regional radar's latest reflectivity frame, ported from `AuraRadarCard`. AEMET's regional
 * product is already a ~240 km circle around a nearby city, so it needs no cropping: shown large, with a
 * plain-Spanish dBZ intensity legend, the site name, and how fresh the frame is.
 *
 * The frame is fetched by the (later) net layer and handed in as an [AuraRadarInfo]; this card only draws
 * it. The Swift `.minimumScaleFactor` shrink becomes plain `maxLines` clipping.
 */
@Composable
fun AuraRadarCard(
    radar: AuraRadarInfo,
    size: AuraSize,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
) {
    val context = LocalContext.current
    AuraSection(stringResource(R.string.card_radar_title).uppercase(), size, modifier = modifier) {
        AuraCard(size) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Image(
                    bitmap = radar.image,
                    contentDescription = stringResource(R.string.card_radar_content_description, radar.siteName),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        // The frame's corners follow the card's, inset the same 8dp the Swift used.
                        .clip(RoundedCornerShape((size.cardCorner.value - 8f).coerceAtLeast(6f).dp)),
                )
                DbzLegend(size)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = subtitle(radar, now, context),
                        fontSize = size.smallSize,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Text(
                        text = stringResource(R.string.card_radar_reflectivity, RANGE_KM),
                        fontSize = size.smallSize - 1,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The regional reflectivity frame is a fixed ~240 km-radius circle — an AEMET product constant. */
private const val RANGE_KM = 240

/** "Radar de Madrid · hace 6 min", or "· ahora" for a just-fetched frame. */
private fun subtitle(radar: AuraRadarInfo, now: Instant, context: Context): String {
    val mins = Duration.between(radar.time, now).toMinutes()
    val freshness = if (mins <= 0) {
        context.getString(R.string.card_radar_now)
    } else {
        context.getString(R.string.card_radar_ago, mins)
    }
    return context.getString(R.string.card_radar_subtitle, radar.siteName, freshness)
}

/**
 * The dBZ intensity ramp AEMET burns into the frame, spelled out: green (light) → magenta (hail), with
 * plain-Spanish rain-intensity labels so the colours mean something without opening a manual.
 */
@Composable
private fun DbzLegend(size: AuraSize) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(red = 0.25f, green = 0.75f, blue = 0.30f),
                            Color(red = 0.95f, green = 0.85f, blue = 0.20f),
                            Color(red = 0.95f, green = 0.55f, blue = 0.15f),
                            Color(red = 0.85f, green = 0.20f, blue = 0.20f),
                            Color(red = 0.80f, green = 0.25f, blue = 0.85f),
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(
                stringResource(R.string.card_radar_intensity_weak),
                stringResource(R.string.card_radar_intensity_moderate),
                stringResource(R.string.card_radar_intensity_strong),
                stringResource(R.string.card_radar_intensity_torrential),
            ).forEach { label ->
                Text(
                    text = label,
                    fontSize = size.smallSize - 3,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
    }
}
