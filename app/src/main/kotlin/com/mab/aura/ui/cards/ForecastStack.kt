package com.mab.aura.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mab.aura.R
import com.mab.aura.core.model.NewsItem
import com.mab.aura.core.model.UVNow
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.ui.theme.Palette
import java.time.Instant

/**
 * The whole "Hoy" screen's card stack, ported from `AuraForecastStack` in `AuraAppCards.swift`. It composes
 * the card suite in **one fixed section order**, with each card shown only when its data is present, and it
 * tunes every card's scrim to the sky behind them so the temperature colours stay legible from a bright noon
 * to a deep night.
 *
 * Like the Swift original this is *just the stack*: no background and no scroll. The host screen wraps it in a
 * scrollable container over an `AuraSky`, exactly as the iOS app wraps the Swift view in a `ScrollView`. This
 * is the layout composition only; the repository-backed live "Hoy" screen (real location, fetched snapshot,
 * pull-to-refresh) is a later step.
 *
 * Two Android notes:
 * - Swift drove the per-card scrim through an `@Environment(\.auraCardScrim)` value; here that is the
 *   [LocalAuraCardScrim] CompositionLocal, provided once around the whole stack (see [cardScrim]).
 * - `heroFillHeight` is a [Dp] rather than a raw `CGFloat`; the host passes its scroll-viewport height to push
 *   the forecast cards below the fold, and the default `0.dp` leaves the hero its natural height (the
 *   continuous, everything-visible layout, matching the offline `aura-render` default).
 */
@Composable
fun AuraForecastStack(
    snapshot: WeatherSnapshot,
    size: AuraSize,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    // Render-only escape hatch, mirroring the Swift `hoursScroll`: false lays the hourly strip out without a
    // horizontal scroll (for a still/preview). The live app leaves it true.
    hoursScroll: Boolean = true,
    // Optional radar frame, fetched and passed by the host (kept out of the snapshot). Null until it loads, so
    // the radar card simply doesn't appear.
    radar: AuraRadarInfo? = null,
    // Optional surface analysis map, fetched and passed by the host (kept out of the snapshot, like radar).
    // Null until it loads, so the surface card simply doesn't appear.
    surface: AuraSurfaceInfo? = null,
    // Optional Noticias stream, fetched and passed by the host. Empty until it loads, so the news card is absent.
    news: List<NewsItem> = emptyList(),
    // When > 0, the hero fills this height (one viewport) so every forecast card falls below the fold: the first
    // screen is just the clean sky and editorial text, the cards revealed on scroll. 0 keeps the hero natural.
    heroFillHeight: Dp = 0.dp,
) {
    // Tune every card's scrim to the sky, then compose. CompositionLocalProvider is Compose's equivalent of the
    // Swift `.environment(\.auraCardScrim, ...)` that wrapped the whole VStack.
    val context = LocalContext.current
    CompositionLocalProvider(LocalAuraCardScrim provides cardScrim(snapshot, now)) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(size.stackSpacing),
        ) {
            // Dissolved hero, optionally stretched to fill the first viewport (see heroFillHeight). Top-aligned
            // so the text sits at the top of the fold, not centred in the stretched space.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (heroFillHeight > 0.dp) Modifier.heightIn(min = heroFillHeight) else Modifier),
                contentAlignment = Alignment.TopStart,
            ) {
                AuraHeroCard(snapshot = snapshot, size = size, now = now)
            }

            snapshot.activeAlert(now)?.let { AuraAlertCard(alert = it, size = size) }

            // Re-anchor the strip to the real current hour: a snapshot served from cache must still start at
            // "now", not at the hour it was built.
            val upcoming = snapshot.upcomingHours(now = now)
            if (upcoming.isNotEmpty()) {
                AuraHourlyCard(hours = upcoming, size = size, scrolls = hoursScroll)
            }
            if (snapshot.days.isNotEmpty()) {
                AuraDailyCard(days = snapshot.days, size = size)
            }
            // One slot, two cards: the Sol arc while the sun is up, the Luna arc once it's dark.
            if (snapshot.isNight(now)) {
                AuraMoonArcCard(snapshot = snapshot, size = size, now = now)
            } else {
                AuraSunArcCard(snapshot = snapshot, size = size, now = now)
            }
            AuraWindCard(snapshot = snapshot, size = size)
            // The observation-station card sits right after the wind rose, only when a station resolved AND its
            // reading is still fresh (matching iOS's phone-only placement). observedStation carries the resolved
            // station's name; observationIsFresh(now) hides the card once the reading ages past 3 h, re-checked
            // against the live clock so a carried reading self-expires with no fetch (same gate as the hero).
            if (snapshot.observedStation != null && snapshot.observationIsFresh(now)) {
                AuraStationCard(snapshot = snapshot, size = size, now = now)
            }
            snapshot.airQuality?.let { AuraAirQualityCard(airQuality = it, size = size) }
            snapshot.uvIndex?.let { uv ->
                AuraUVCard(
                    uvIndex = uv,
                    size = size,
                    hourly = snapshot.uvHourly ?: emptyList(),
                    now = now,
                    cloudy = UVNow.cloudy(snapshot),
                )
            }
            radar?.let { AuraRadarCard(radar = it, size = size, now = now) }
            // The synoptic surface analysis sits right after radar: both are AEMET big-picture maps, phone-only.
            surface?.let { AuraSurfaceCard(surface = it, size = size, now = now) }
            snapshot.bulletin?.takeIf { it.isNotEmpty() }?.let { bulletin ->
                AuraBulletinCard(phenomenon = snapshot.bulletinPhenomenon, text = bulletin, size = size)
            }
            if (news.isNotEmpty()) {
                AuraNewsCard(items = news, size = size, now = now)
            }

            // Every third-party source present is credited alongside AEMET (each is CC-BY, which requires
            // attribution): MITECO when the air-quality card shows, Copernicus (CAMS, via Open-Meteo) when the
            // hourly UV curve does.
            Text(
                text = forecastCredit(
                    hasAir = snapshot.airQuality != null,
                    hasHourlyUV = (snapshot.uvHourly ?: emptyList()).isNotEmpty(),
                    context = context,
                ),
                fontSize = size.smallSize - 4,   // Swift's literal 14 on phone
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

/**
 * The attribution line: AEMET always, plus each third-party source actually shown, in a natural Spanish list
 * ("AEMET", "AEMET y MITECO", "AEMET, MITECO y Copernicus", "AEMET y Copernicus"). Copernicus = the CAMS UV
 * via Open-Meteo behind the hourly curve. Direct port of the Swift `credit` static.
 */
internal fun forecastCredit(hasAir: Boolean, hasHourlyUV: Boolean, context: Context): String {
    val sources = buildList {
        add("AEMET")
        if (hasAir) add("MITECO")
        if (hasHourlyUV) add("Copernicus")
    }
    val list = if (sources.size == 1) {
        sources[0]
    } else {
        context.getString(
            R.string.card_forecast_list_conjunction,
            sources.dropLast(1).joinToString(", "),
            sources.last(),
        )
    }
    return context.getString(R.string.card_forecast_credit, list)
}

/**
 * The scrim for the current sky. Uses the true day/night for the hour (not just the code's `n` suffix, which a
 * cache-built snapshot can lack) so a nightfall with no current-sky code still reads as night and skips the
 * extra darkening. Direct port of the Swift `cardScrim(for:now:)`.
 */
internal fun cardScrim(snapshot: WeatherSnapshot, now: Instant): AuraCardScrim {
    if (snapshot.isNight(now)) return AuraCardScrim(top = 0.0f, bottom = 0.08f)
    val (top, bottom) = Palette.cardScrim(snapshot.currentSky)
    return AuraCardScrim(top = top, bottom = bottom)
}
