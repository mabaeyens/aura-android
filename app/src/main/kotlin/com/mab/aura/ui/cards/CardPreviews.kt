package com.mab.aura.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mab.aura.core.model.NewsItem
import com.mab.aura.core.model.NewsSource
import com.mab.aura.core.model.WeatherAlert
import java.net.URI
import java.time.Duration
import java.time.Instant

/**
 * `@Preview` gallery for the card suite, so each card is checkable in Android Studio before the full
 * `AuraForecastStack` exists. The cards are laid over a noon-sky blue: the frosted [AuraCard] is a
 * translucent dark pane (see its KDoc for why there's no real blur), so it only reads correctly over a
 * sky, not a white canvas. One `@Preview` per card; add to this file as more cards land.
 */

// A fixed "now" so the News card's relative times ("hace 2 h") are stable across preview renders.
private val previewNow: Instant = Instant.parse("2026-08-25T12:00:00Z")

/** A mid-day sky panel, matching how the cards float over `AuraSky` in the app. */
@Composable
private fun SkyPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF4A82C4), Color(0xFF2C5688))))
            .padding(16.dp),
    ) {
        content()
    }
}

@Preview(name = "Bulletin card", widthDp = 380)
@Composable
private fun BulletinCardPreview() {
    SkyPanel {
        AuraBulletinCard(
            phenomenon = "Tormentas",
            text = "Cielo nuboso con probables chubascos y tormentas por la tarde. " +
                "Temperaturas máximas en descenso. Viento del suroeste flojo a moderado.",
            size = AuraSize.Phone,
        )
    }
}

@Preview(name = "Alert card (collapsed)", widthDp = 380)
@Composable
private fun AlertCardPreview() {
    SkyPanel {
        AuraAlertCard(
            alert = WeatherAlert(
                level = WeatherAlert.Level.NARANJA,
                event = "Aviso de temperaturas máximas de nivel naranja",
                phenomenon = "Temperatura máxima",
                zona = "610401",
                areaDesc = "Valle del Almanzora y Los Vélez",
                onset = previewNow,
                expires = previewNow.plus(Duration.ofHours(6)),
            ),
            size = AuraSize.Phone,
        )
    }
}

@Preview(name = "News card", widthDp = 380)
@Composable
private fun NewsCardPreview() {
    SkyPanel {
        AuraNewsCard(
            items = listOf(
                sampleNews("El tiempo se complica: lluvias intensas este fin de semana en el norte",
                    NewsSource.RTVE, Duration.ofMinutes(2)),
                sampleNews("Aviso naranja por tormentas en el interior peninsular",
                    NewsSource.AEMET, Duration.ofHours(3)),
                sampleNews("¿Por qué ha refrescado tanto? La explicación de la DANA",
                    NewsSource.METEORED, Duration.ofDays(1)),
                sampleNews("Balance del verano meteorológico en España",
                    NewsSource.AEMET_BLOG, Duration.ofDays(2)),
            ),
            size = AuraSize.Phone,
            now = previewNow,
        )
    }
}

private var previewLink = 0
private fun sampleNews(title: String, source: NewsSource, ago: Duration): NewsItem = NewsItem(
    title = title,
    link = URI("https://example.org/aura-preview/${previewLink++}"),
    source = source,
    date = previewNow.minus(ago),
)
