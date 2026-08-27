package com.mab.aura.ui.hoy

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mab.aura.R
import com.mab.aura.core.model.NewsItem
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.ui.cards.AuraForecastStack
import com.mab.aura.ui.cards.AuraRadarInfo
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
fun HoyScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenLocations: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    now: Instant = Instant.now(),
) {
    val viewModel: HoyViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val heroFamily by viewModel.heroFamily.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Returning from Ajustes after a key was just entered: retry the fetch the no-key state was blocking.
    // The effect re-runs each time the screen re-enters composition (i.e. on every return from Settings).
    LaunchedEffect(Unit) {
        if (viewModel.state.value is HoyUiState.NeedsApiKey) viewModel.load()
    }

    // Coarse-location prompt. Android splits acquisition (LocationProvider) from the *request* UI, which is
    // the screen's job. Until the favourites/add-location UI exists, Hoy is the only place that can ask, and
    // the nearest-city resolution needs it. Ask once, only after we have working weather (a key is present),
    // so the very first no-key screen isn't fronted by a location dialog; on grant, reload to use the fix.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.load() }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val hasWeather = state is HoyUiState.Content
    LaunchedEffect(hasWeather) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasWeather && !permissionAsked && !granted) {
            permissionAsked = true
            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The sky backdrop follows the shown snapshot (null → a neutral high-noon sky), so even the loading
        // and no-key states sit over a real sky rather than a blank surface.
        val backdrop = (state as? HoyUiState.Content)?.snapshot
        AuraSky(snapshot = backdrop, modifier = Modifier.fillMaxSize(), now = now, family = heroFamily)

        when (val s = state) {
            HoyUiState.Loading -> CenteredMessage {
                CircularProgressIndicator(color = Color.White)
            }

            HoyUiState.NeedsApiKey -> CenteredMessage {
                Message(
                    title = stringResource(R.string.hoy_needs_key_title),
                    body = stringResource(R.string.hoy_needs_key_body),
                )
            }

            is HoyUiState.Content -> {
                // One banner slot, so a data problem (offline, rate-limited) takes priority over the softer
                // "showing a default location" note; the latter shows only when the fetch itself was fine.
                val effectiveNotice = s.notice ?: locationFallbackText(context, s.locationFallback, permissionAsked)
                HoyContent(snapshot = s.snapshot, notice = effectiveNotice, now = now, radar = s.radar, news = s.news)
            }

            is HoyUiState.Error -> CenteredMessage {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Message(title = stringResource(R.string.hoy_error_title), body = s.message)
                    Button(onClick = { viewModel.load() }) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }

        // The only chrome over the sky is the gear top-right, clear of the status bar and white to read
        // against it. Tapping it opens a small menu rather than jumping straight to Ajustes: the saved-locations
        // pin used to live top-left but collided with the city name, so its entry point moved in here.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(4.dp),
        ) {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.hoy_menu_content_desc), tint = Color.White)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hoy_menu_locations)) },
                    leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenLocations()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hoy_menu_settings)) },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenSettings()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hoy_menu_help)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenHelp()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hoy_menu_about)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenAbout()
                    },
                )
            }
        }
    }
}

/**
 * The Spanish notice for a location fallback, or null for none. We only mention a missing permission once the
 * screen has actually asked ([permissionAsked]): before the prompt appears, saying "no permission" would be
 * premature. Services-off and no-fix are surfaced straight away, since the permission prompt can't fix either.
 */
private fun locationFallbackText(context: Context, fallback: LocationFallback?, permissionAsked: Boolean): String? =
    when (fallback) {
        null -> null
        LocationFallback.PermissionDenied ->
            if (permissionAsked) context.getString(R.string.hoy_location_permission_denied) else null
        LocationFallback.ServicesOff -> context.getString(R.string.hoy_location_services_off)
        LocationFallback.NoFix -> context.getString(R.string.hoy_location_no_fix)
    }

/** The weather itself: the card stack in a vertical scroll over the sky, as the app lays it out. */
@Composable
private fun HoyContent(
    snapshot: WeatherSnapshot,
    notice: String?,
    now: Instant,
    radar: AuraRadarInfo?,
    news: List<NewsItem>,
) {
    // [BoxWithConstraints] hands us the viewport height (maxHeight) before the scroll, so we can stretch the
    // hero to fill one screenful and push every forecast card *fully* below the fold, matching iOS: the opening
    // screen is just the sky and the editorial summary, nothing of the next card showing. The fill height is
    // the viewport minus the top inset and the Column's top padding, so the hero reaches the bottom edge and
    // the first card (plus the stack's gap) lands just off-screen. A scroll cue takes the place of the peeking
    // title (see [ScrollHint]).
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val heroFill = (maxHeight - insets.calculateTopPadding() - 12.dp).coerceAtLeast(0.dp)
        val scroll = rememberScrollState()

        // Option B — a fixed top scrim: a soft dark-to-transparent gradient over the sky, darkest at the very
        // top and gone by ~45% down, so the hero text sits on real contrast whatever the art behind it. Fixed
        // (not in the scroll), so it only ever darkens the top band; the cards scroll up clear of it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight * 0.45f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
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
            AuraForecastStack(
                snapshot = snapshot,
                size = AuraSize.Phone,
                now = now,
                radar = radar,
                news = news,
                heroFillHeight = heroFill,
            )
        }

        // The scroll cue: a gentle chevron pinned above the nav bar, shown only at the top of the scroll and
        // fading away the moment the user starts scrolling (like iOS's hero chevron).
        ScrollHint(
            visible = scroll.value == 0,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = insets.calculateBottomPadding() + 8.dp),
        )
    }
}

/**
 * A downward chevron hinting that the forecast cards are below the fold. It bobs slowly to catch the eye and
 * fades out as soon as the scroll leaves the top, so it never sits over the cards. A faint dark twin behind
 * the white glyph gives it the same halo the hero text uses, so it reads over a pale sky too.
 */
@Composable
private fun ScrollHint(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(targetValue = if (visible) 0.9f else 0f, label = "scrollHintAlpha")
    val bob by rememberInfiniteTransition(label = "scrollHintBob").animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scrollHintOffset",
    )
    Box(
        modifier = modifier
            .offset(y = bob.dp)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.35f),
            modifier = Modifier.size(34.dp).offset(y = 1.dp),
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.hoy_scroll_hint),
            tint = Color.White,
            modifier = Modifier.size(34.dp),
        )
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
