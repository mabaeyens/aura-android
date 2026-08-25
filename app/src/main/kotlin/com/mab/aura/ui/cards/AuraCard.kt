package com.mab.aura.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The shared foundation for the Aura card suite, ported from the top of `AuraAppCards.swift`
 * (the size class, the frosted `AuraCard`, the section-title label, and the precipitation colour).
 * Every card in `ui/cards` builds on these.
 *
 * Two deliberate differences from the Swift original:
 *
 * 1. **Phone only.** The Swift [AuraSize] carried a `watch` case because the iPhone and the Apple Watch
 *    composed the *same* cards at two sizes. Aura Android is phone-only, so the enum keeps a single
 *    [AuraSize.Phone] case with only the phone metrics. It stays an enum (rather than loose constants)
 *    so every card signature reads `size: AuraSize`, matching the Swift 1:1 and leaving a clean seam if
 *    a second size class ever returns.
 *
 * 2. **No true frosted glass.** SwiftUI drew the cards over `.ultraThinMaterial`, a live backdrop blur
 *    of the sky behind them. Android has no cheap backdrop blur (`Modifier.blur` blurs a view's *own*
 *    content, not what's behind it, and RenderEffect backdrop blur is API 33+, while we target minSdk
 *    26). So [AuraCard] approximates the frost with a translucent dark pane, the same bottom-weighted
 *    black scrim the Swift card rides inside, and a hairline light border. The sky still reads through
 *    and the white text stays legible; it simply isn't blurred. A real haze pass is a possible future
 *    enhancement, not a blocker.
 */

/** A brighter blue than [com.mab.aura.ui.theme.Palette.tempBlue] for precipitation labels, so the rain
 *  chance stays legible on the dark frosted cards even over a cloudy, greyed-down sky. */
internal val auraPrecipColor = Color(red = 0.52f, green = 0.80f, blue = 1.0f)

/** Phone metrics. The Swift enum resized these for the Watch; here there is only the phone. */
enum class AuraSize {
    Phone;

    // Layout dimensions (density-independent pixels).
    val stackSpacing: Dp get() = 16.dp
    val cardPadding: Dp get() = 18.dp
    val cardCorner: Dp get() = 26.dp
    val heroIcon: Dp get() = 66.dp
    val iconSize: Dp get() = 27.dp
    val hourGap: Dp get() = 20.dp
    val rowGap: Dp get() = 16.dp

    // Font sizes (scale-independent pixels). Swift used one CGFloat for both dimensions and font sizes;
    // Compose splits them, so the values that feed `fontSize` live here as `sp`.
    val heroTemp: TextUnit get() = 78.sp
    val titleSize: TextUnit get() = 16.sp
    val bodySize: TextUnit get() = 22.sp
    val smallSize: TextUnit get() = 18.sp
}

/**
 * Cards routinely derive a font from a base metric, e.g. Swift's `size.bodySize - 4`. Compose's
 * [TextUnit] has no built-in subtraction of a scalar, so this small operator keeps the ports reading
 * exactly like the Swift. Safe because every Aura font metric is expressed in `sp`.
 */
internal operator fun TextUnit.minus(points: Int): TextUnit = (this.value - points).sp

/**
 * The frosted card. Padding, a translucent dark pane with a bottom-weighted scrim, and a hairline
 * border, matching the look of the Swift `AuraCard` (see the file KDoc for why there's no real blur).
 */
@Composable
internal fun AuraCard(
    size: AuraSize,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(size.cardCorner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // The translucent pane the sky reads through, then the same black 0.06 -> 0.24 vertical
            // scrim the Swift card rides inside: it lifts contrast where the hero sky is brightest
            // (the bottom) while barely touching the top, so the corners stay clean.
            .background(Color(red = 0.08f, green = 0.10f, blue = 0.14f, alpha = 0.55f))
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.24f)),
                ),
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(size.cardPadding),
        content = content,
    )
}

/**
 * A small uppercase section label above its card, like Apple Weather's "HOURLY FORECAST". The Swift
 * `.auraSectionTitle(_:_:)` was a `View` modifier that stacked the label over `self`; here it's a
 * wrapper composable, so a card is written as `AuraSection("NOTICIAS", size) { AuraCard(size) { ... } }`.
 */
@Composable
internal fun AuraSection(
    title: String,
    size: AuraSize,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = size.titleSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
            color = Color.White.copy(alpha = 0.72f),
        )
        content()
    }
}
