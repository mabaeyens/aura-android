package com.mab.aura.ui.hoy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.ui.cards.AuraForecastStack
import com.mab.aura.ui.cards.AuraSize
import com.mab.aura.ui.sky.AuraSky
import java.time.Instant

/**
 * The live "Hoy" screen: the ported card suite ([AuraForecastStack]) over the procedural [AuraSky], now
 * driven by real data from the [HoyViewModel] instead of a sample snapshot. This is the payoff of the port —
 * the whole Compose UI finally hangs together over the repository.
 *
 * Like the Swift, the sky is the full-bleed background and the stack scrolls over it; the screen owns the
 * scroll and the background (the stack itself is just the cards). Each [HoyUiState] gets its own presentation:
 * a spinner while loading, the scrolling stack when there's weather, and a centred message for the
 * no-key / error states, all over a sky backdrop so the screen never flashes a bare surface.
 */
@Composable
fun HoyScreen(modifier: Modifier = Modifier, now: Instant = Instant.now()) {
    val viewModel: HoyViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        // The sky backdrop follows the shown snapshot (null → a neutral high-noon sky), so even the loading
        // and no-key states sit over a real sky rather than a blank surface.
        val backdrop = (state as? HoyUiState.Content)?.snapshot
        AuraSky(snapshot = backdrop, modifier = Modifier.fillMaxSize(), now = now)

        when (val s = state) {
            HoyUiState.Loading -> CenteredMessage {
                CircularProgressIndicator(color = Color.White)
            }

            HoyUiState.NeedsApiKey -> CenteredMessage {
                Message(
                    title = "Añade tu clave de AEMET",
                    body = "Aura necesita una clave de la API de AEMET para mostrar el tiempo. La entrada de " +
                        "la clave llega con la pantalla de Ajustes.",
                )
            }

            is HoyUiState.Content -> HoyContent(snapshot = s.snapshot, notice = s.notice, now = now)

            is HoyUiState.Error -> CenteredMessage {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Message(title = "No se pudo cargar", body = s.message)
                    Button(onClick = { viewModel.load() }) { Text("Reintentar") }
                }
            }
        }
    }
}

/** The weather itself: the card stack in a vertical scroll over the sky, as the app lays it out. */
@Composable
private fun HoyContent(snapshot: WeatherSnapshot, notice: String?, now: Instant) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // A non-blocking notice (e.g. "offline, showing last data") above the still-useful cached cards.
        if (notice != null) {
            Text(
                text = notice,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        AuraForecastStack(snapshot = snapshot, size = AuraSize.Phone, now = now)
    }
}

/** Centres arbitrary content over the sky, clear of the system bars. */
@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A titled explanatory message, white on the sky. */
@Composable
private fun Message(title: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center)
        Text(
            body,
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}
