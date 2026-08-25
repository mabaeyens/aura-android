package com.mab.aura.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mab.aura.ui.cards.AuraCard
import com.mab.aura.ui.cards.AuraSize
import kotlinx.coroutines.launch

/**
 * A frosted [AuraCard] that is tappable to open a detail [sheet], ported from the Swift
 * `View.auraDetail(_:detail:)` modifier in `AuraScaleSheets.swift`. It is a drop-in replacement for
 * `AuraCard`: a card that carries a reference sheet swaps its `AuraCard(size) { ... }` for
 * `AuraDetailCard(size, sheet = { onClose -> ...Sheet(...) }) { ... }` — same `size` and content, plus the
 * sheet to present.
 *
 * Two ports of the Swift shape:
 * - Swift attached the affordance only at `.phone` size (no room on the Watch). Aura Android is phone-only,
 *   so there is no size gate; every use is a phone use. That is why this takes no separate flag.
 * - Swift used SF Symbol `hand.tap.fill` for the corner "there's more" cue. Material's core icon set (the
 *   only one we pull; see the app build file) has no hand-tap glyph, so this uses `Icons.Filled.Info` in the
 *   same muted white — a plain "tap for details" cue. Same intent, an available glyph.
 *
 * The sheet slides up as a Material [ModalBottomSheet]: the drag handle and the medium/large detents come
 * for free, matching the Swift `.presentationDetents([.medium, .large])`. [sheet] receives an `onClose`
 * callback so the sheet's own corner close button can dismiss it with the same animation as a swipe-down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuraDetailCard(
    size: AuraSize,
    modifier: Modifier = Modifier,
    sheet: @Composable (onClose: () -> Unit) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showing by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    Box(
        // The whole card is the tap target, matching the Swift `.contentShape(Rectangle()).onTapGesture`.
        modifier = modifier.clickable { showing = true },
        contentAlignment = Alignment.TopEnd,
    ) {
        AuraCard(size = size, content = content)
        // The affordance: a small muted glyph in the top-trailing corner the cards leave empty. It sits
        // inside the tappable Box, so a tap on it still opens the sheet (no Swift `allowsHitTesting` needed).
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.padding(12.dp),
        )
    }

    if (showing) {
        // Animate the sheet closed, then drop it from composition once the animation settles.
        val close: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) showing = false
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showing = false },
            sheetState = sheetState,
            containerColor = Color.Transparent, // each sheet paints its own dark gradient, edge to edge
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            sheet(close)
        }
    }
}
