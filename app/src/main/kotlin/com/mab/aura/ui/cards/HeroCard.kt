package com.mab.aura.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.aura.R
import com.mab.aura.core.hero.HeroBackground
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.text.ForecastPhrase
import com.mab.aura.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId

/**
 * The dissolved hero, ported from `AuraHeroCard` in `AuraAppCards.swift`. Unlike every other card it has
 * no [AuraCard] frame: the sky (and the sun/moon disc `AuraSky` draws at the true position) carries the
 * top of the screen, and over it float only the editorial temperature and two lines of prose. Text lines
 * up with the cards below through the same horizontal inset ([AuraSize.cardPadding]).
 *
 * Two small differences from the Swift, both cosmetic:
 *
 * 1. **No rounded-design digits.** SwiftUI drew the temperature in the `.rounded` system face. Android has
 *    no bundled rounded font, so this uses the default face bold. Swapping in a rounded font is a later
 *    polish, not a blocker.
 * 2. **No auto-shrink.** SwiftUI's `minimumScaleFactor` shrank a line to fit rather than truncate. Compose
 *    text has no cheap equivalent at this BOM, so lines cap with [maxLines] instead. The 78sp temperature
 *    on one line and the two-line headline fit the phone width; if a very long place name ever clips, an
 *    autosize pass can come later.
 */
@Composable
fun AuraHeroCard(
    snapshot: WeatherSnapshot,
    size: AuraSize,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    // The Spanish time-of-day word after the city ("MADRID · Atardecer"), from the same sun-path bucket the
    // hero background selector uses, so the label tracks true sunrise/sunset.
    val momentLabel = when (HeroBackground.Time.from(now, snapshot.sunrise, snapshot.sunset, zone)) {
        HeroBackground.Time.DAWN -> stringResource(R.string.card_hero_moment_dawn)
        HeroBackground.Time.MORNING -> stringResource(R.string.card_hero_moment_morning)
        HeroBackground.Time.NOON -> stringResource(R.string.card_hero_moment_noon)
        HeroBackground.Time.AFTERNOON -> stringResource(R.string.card_hero_moment_afternoon)
        HeroBackground.Time.DUSK -> stringResource(R.string.card_hero_moment_dusk)
        HeroBackground.Time.NIGHT -> stringResource(R.string.card_hero_moment_night)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = size.cardPadding)
            .padding(top = 6.dp),
    ) {
        Text(
            text = "${snapshot.localidad.uppercase()} · $momentLabel",
            fontSize = size.bodySize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = heroShadow,
        )

        Text(
            text = snapshot.heroTemp(now, zone)?.let { "$it°" } ?: "—",
            fontSize = size.heroTemp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            style = heroShadow,
        )

        Text(
            text = ForecastPhrase.headline(snapshot, now, zone),
            fontSize = size.bodySize,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = heroShadow,
        )

        Text(
            // Máx/Mín · viento · humedad · lluvia, folded into a sentence by ForecastPhrase. This is the
            // smallest hero line, so it gets solid white and a touch more weight to hold contrast over a
            // bright, washed-out sky (the soft-alpha grey it used before disappeared into a pale noon).
            text = ForecastPhrase.dataline(snapshot, now),
            fontSize = size.bodySize - 4,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            style = heroShadow,
        )

        // An active aviso reads as its sign plus a one-word summary ("Calor", "Tormentas"), tinted with the
        // warning level's colour. The full text lives in the tappable aviso card below the fold; here it's a
        // glance.
        snapshot.activeAlert(now)?.let { alert ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                // A dark capsule behind the glance, matching iOS: without it a yellow (amarillo) aviso washes
                // out over a pale midday sky. The pill keeps the level colour legible whatever the art behind it.
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(Color.Black.copy(alpha = 0.30f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Palette.alert(alert.level),
                    modifier = Modifier.size(size.bodySize.value.dp),
                )
                Text(
                    text = alert.shortLabel,
                    fontSize = size.bodySize,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.alert(alert.level),
                    style = heroShadow,
                )
            }
        }
    }
}

/**
 * A dark halo behind the hero text, matching the Swift card's `.shadow`. It keeps white text legible over a
 * bright noon sky without reintroducing a panel; the sky's own scrim does the rest. Deepened from the first
 * port (0.35 alpha / 9 blur was too faint under a pale sky): a wider, darker blur reads as a soft glow the
 * text always sits on, whatever the art behind it.
 */
private val heroShadow = TextStyle(
    shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 16f),
)
