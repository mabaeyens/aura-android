package com.mab.aura.ui.cards

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mab.aura.R
import com.mab.aura.ui.sheets.AuraSurfaceSheet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A fetched surface-analysis map plus the context the card shows around it, ported from `AuraSurfaceInfo` in
 * `AuraAppCards.swift`. Not stored in `WeatherSnapshot` (the image bytes would bloat the cached snapshot) —
 * the app fetches and passes it separately. The Swift `Image` becomes a decoded, already-rotated Compose
 * [ImageBitmap] (see [com.mab.aura.data.SurfaceAnalysisRepository]).
 */
data class AuraSurfaceInfo(
    val image: ImageBitmap,
    /** The map's nominal 00/12 UTC issue slot, for the "valid at" line. */
    val issue: Instant,
)

/**
 * AEMET's surface analysis chart, ported from `AuraSurfaceCard`. The synoptic, big-picture map (isobars,
 * high/low centres, warm/cold/occluded/stationary fronts over Europe and the North Atlantic) that explains
 * *why* the local weather is what it is. Reissued every 12 h and fetched at most that often.
 *
 * The map is wide and dense, so it is shown fit-to-width and made zoomable inline. Two Android notes:
 * - **Zoom without fighting the scroll.** The card lives inside the "Hoy" vertical scroll. Pinch always
 *   zooms; a one-finger drag pans the map *only when zoomed in* and otherwise passes straight through to the
 *   scroll. That is what `transformable(..., canPan = { scale > 1f })` buys us — `canPan` is the Compose
 *   analogue of iOS gating its `DragGesture` on `scale > 1`. Double-tap animates back to fit.
 * - **The legend is a button, not the whole card.** Because the map area consumes pinch and drag, the card
 *   can't use the whole-card [com.mab.aura.ui.sheets.AuraDetailCard] tap affordance. A small info button in
 *   the corner opens the symbols sheet instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraSurfaceCard(
    surface: AuraSurfaceInfo,
    size: AuraSize,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Live zoom/pan state. Plain state (not Animatable) keeps the gesture snappy; only the double-tap reset
    // animates, in a coroutine below. Offset is in pixels, applied via graphicsLayer so it never re-lays-out.
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = if (scale > 1f) clampOffset(offset + panChange, scale, boxSize) else Offset.Zero
    }

    AuraSection(stringResource(R.string.card_surface_title).uppercase(), size, modifier = modifier) {
        AuraCard(size) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The map's corners follow the card's, inset the same 8dp the radar card uses.
                        .clip(RoundedCornerShape((size.cardCorner.value - 8f).coerceAtLeast(6f).dp))
                        .onSizeChanged { boxSize = it }
                        .transformable(state = transformState, canPan = { scale > 1f })
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    // Animate scale and offset back to fit together. Capture the start values
                                    // so both interpolate from where the gesture left them.
                                    val fromScale = scale
                                    val fromOffset = offset
                                    scope.launch {
                                        animate(0f, 1f) { t, _ ->
                                            scale = fromScale + (1f - fromScale) * t
                                            offset = fromOffset * (1f - t)
                                        }
                                    }
                                },
                            )
                        },
                ) {
                    Image(
                        bitmap = surface.image,
                        contentDescription = stringResource(R.string.card_surface_content_description),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )

                    // Legend affordance: a muted info glyph in the corner, opening the symbols sheet. It sits
                    // over the map but takes its own tap, so it never triggers a zoom/pan.
                    var showLegend by remember { mutableStateOf(false) }
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .pointerInput(Unit) { detectTapGestures { showLegend = true } }
                            .padding(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.card_surface_legend_action),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (showLegend) {
                        val close: () -> Unit = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) showLegend = false
                            }
                        }
                        ModalBottomSheet(
                            onDismissRequest = { showLegend = false },
                            sheetState = sheetState,
                            containerColor = Color.Transparent,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        ) {
                            AuraSurfaceSheet(onClose = close)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.card_surface_valid, validTime(surface.issue)),
                        fontSize = size.smallSize,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Text(
                        text = stringResource(R.string.card_surface_hint),
                        fontSize = size.smallSize - 1,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Zoom ceiling: enough to read the densest isobar labels on a phone without turning the map to mush. */
private const val MAX_ZOOM = 5f

/** The "HH:00" UTC issue slot, formatted for the "valid at" line (the map itself carries the full validity). */
private val slotTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

private fun validTime(issue: Instant): String = slotTimeFormatter.format(issue)

/**
 * Keep the zoomed map from being panned off-view: at scale s the image overflows the box by (s−1)×size on
 * each axis, so the reachable translation is ±(s−1)×size/2. Clamp both axes to that.
 */
private fun clampOffset(raw: Offset, scale: Float, box: IntSize): Offset {
    val maxX = (scale - 1f) * box.width / 2f
    val maxY = (scale - 1f) * box.height / 2f
    return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
}
