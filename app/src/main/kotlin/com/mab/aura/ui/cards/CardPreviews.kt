package com.mab.aura.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mab.aura.core.air.AirComponent
import com.mab.aura.core.air.AirQuality
import com.mab.aura.core.model.DaySnapshot
import com.mab.aura.core.model.HourSlot
import com.mab.aura.core.model.NewsItem
import com.mab.aura.core.model.NewsSource
import com.mab.aura.core.model.UVHourSlot
import com.mab.aura.core.model.WeatherAlert
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.uv.UVIndex
import com.mab.aura.core.wind.WindDirection
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

@Preview(name = "Hero card", widthDp = 380)
@Composable
private fun HeroCardPreview() {
    SkyPanel {
        AuraHeroCard(snapshot = sampleSnapshot, size = AuraSize.Phone, now = previewNow)
    }
}

@Preview(name = "Hourly card", widthDp = 380)
@Composable
private fun HourlyCardPreview() {
    SkyPanel {
        // scrolls = false so the preview lays the first five hours out edge to edge (Android Studio's
        // preview can render a horizontal scroll, but the still is clearer with all five visible).
        AuraHourlyCard(hours = sampleSnapshot.hours, size = AuraSize.Phone, scrolls = false)
    }
}

@Preview(name = "Daily card", widthDp = 380)
@Composable
private fun DailyCardPreview() {
    SkyPanel {
        AuraDailyCard(days = sampleSnapshot.days, size = AuraSize.Phone)
    }
}

@Preview(name = "Wind card", widthDp = 380)
@Composable
private fun WindCardPreview() {
    SkyPanel {
        AuraWindCard(snapshot = sampleSnapshot, size = AuraSize.Phone)
    }
}

@Preview(name = "Air quality card", widthDp = 380)
@Composable
private fun AirQualityCardPreview() {
    SkyPanel {
        AuraAirQualityCard(airQuality = sampleAirQuality, size = AuraSize.Phone)
    }
}

@Preview(name = "UV card", widthDp = 380)
@Composable
private fun UVCardPreview() {
    SkyPanel {
        AuraUVCard(
            uvIndex = UVIndex(8),
            hourly = sampleUvHourly,
            now = previewNow,
            size = AuraSize.Phone,
            cloudy = false,
        )
    }
}

@Preview(name = "Radar card", widthDp = 380)
@Composable
private fun RadarCardPreview() {
    // A flat dark placeholder frame, enough to check the rounded image, the dBZ legend and the labels;
    // the real reflectivity bytes come from the (later) net layer.
    val frame = remember { previewRadarBitmap() }
    SkyPanel {
        AuraRadarCard(
            radar = AuraRadarInfo(image = frame, siteName = "Madrid", time = previewNow.minus(Duration.ofMinutes(6))),
            size = AuraSize.Phone,
            now = previewNow,
        )
    }
}

@Preview(name = "Sun arc card", widthDp = 380)
@Composable
private fun SunArcCardPreview() {
    SkyPanel {
        AuraSunArcCard(snapshot = sampleSnapshot, size = AuraSize.Phone, now = previewNow)
    }
}

@Preview(name = "Moon arc card", widthDp = 380)
@Composable
private fun MoonArcCardPreview() {
    SkyPanel {
        AuraMoonArcCard(snapshot = sampleSnapshot, size = AuraSize.Phone, now = previewNow)
    }
}

// One representative snapshot the three forecast cards read from: a warm, mostly clear Madrid afternoon
// with a passing shower later, six days ahead, and a low sun so the hero shows a real moment label.
private val sampleSnapshot = WeatherSnapshot(
    ine = "28079",
    localidad = "Madrid",
    provincia = "Madrid",
    tempMin = 18,
    tempMax = 33,
    humedadMax = 55,
    currentTemp = 31,
    currentSky = "12",
    currentHumidity = 34,
    currentPrecipProb = 10,
    windSpeed = 14,
    windDirection = WindDirection.SO,
    windGust = 34,
    // Madrid summer sun (UTC): ~07:00 local sunrise, ~21:15 local sunset, so previewNow (14:00 local) reads
    // as "Mediodía"/"Tarde".
    sunrise = Instant.parse("2026-08-25T05:00:00Z"),
    sunset = Instant.parse("2026-08-25T19:15:00Z"),
    // Madrid, so the sun/moon arc cards can solve civil twilight, the day-over-day delta and the moon's
    // own salida/puesta from real coordinates.
    latitude = 40.4168,
    longitude = -3.7038,
    hours = listOf(
        HourSlot(hour = 14, temp = 31, sky = "12", precipProb = 0),
        HourSlot(hour = 15, temp = 32, sky = "12", precipProb = 0),
        HourSlot(hour = 16, temp = 33, sky = "13", precipProb = 15),
        HourSlot(hour = 17, temp = 32, sky = "43", precipProb = 35),
        HourSlot(hour = 18, temp = 30, sky = "14", precipProb = 20),
        HourSlot(hour = 19, temp = 28, sky = "12", precipProb = 5),
        HourSlot(hour = 20, temp = 25, sky = "11", precipProb = 0),
        HourSlot(hour = 21, temp = 23, sky = "11n", precipProb = 0),
    ),
    days = listOf(
        DaySnapshot(date = previewNow, min = 18, max = 33, sky = "12", probPrecip = 10),
        DaySnapshot(date = previewNow.plus(Duration.ofDays(1)), min = 19, max = 34, sky = "11", probPrecip = 0),
        DaySnapshot(date = previewNow.plus(Duration.ofDays(2)), min = 20, max = 35, sky = "11", probPrecip = 0),
        DaySnapshot(date = previewNow.plus(Duration.ofDays(3)), min = 17, max = 29, sky = "43", probPrecip = 45),
        DaySnapshot(date = previewNow.plus(Duration.ofDays(4)), min = 15, max = 26, sky = "14", probPrecip = 30),
        DaySnapshot(date = previewNow.plus(Duration.ofDays(5)), min = 16, max = 28, sky = "13", probPrecip = 15),
    ),
    updated = previewNow,
)

private var previewLink = 0
private fun sampleNews(title: String, source: NewsSource, ago: Duration): NewsItem = NewsItem(
    title = title,
    link = URI("https://example.org/aura-preview/${previewLink++}"),
    source = source,
    date = previewNow.minus(ago),
)

// A category-3 ("Regular") reading driven by O₃, with three of the five pollutants measured so the
// breakdown row shows both real chips and greyed-out ones (PM10, SO₂).
private val sampleAirQuality = AirQuality.create(
    category = 3,
    partial = false,
    pollutant = "O3",
    station = "Retiro",
    distanceKm = 1.7,
    measured = previewNow,
    components = listOf(
        AirComponent(pollutant = "NO2", value = 34.0, station = "Retiro", distanceKm = 1.7, measured = previewNow),
        AirComponent(pollutant = "O3", value = 115.0, station = "Retiro", distanceKm = 1.7, measured = previewNow),
        AirComponent(pollutant = "PM2.5", value = 12.0, station = "Retiro", distanceKm = 1.7, measured = previewNow),
    ),
)

// One day of CAMS UV hours anchored on the feed's local midnight (00:00 UTC here), a summer curve peaking
// just after solar noon. previewNow (12:00 UTC) lands mid-curve, so the strip shows a real "Ahora" value.
private val sampleUvHourly: List<UVHourSlot> = run {
    val start = Instant.parse("2026-08-25T00:00:00Z")
    val uvByHour = doubleArrayOf(
        0.0, 0.0, 0.0, 0.0, 0.0, 0.6, 1.8, 3.4, 5.1, 6.8, 8.2, 8.6,
        8.1, 6.4, 4.3, 2.6, 1.2, 0.4, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
    )
    uvByHour.mapIndexed { h, uv -> UVHourSlot(start.plusSeconds(h * 3600L), uv, uv) }
}

// A flat dark square standing in for a radar reflectivity frame in previews. android.graphics is available
// under layoutlib, so a plain 4:3 bitmap is enough to exercise the card's image/legend/label layout.
private fun previewRadarBitmap(): ImageBitmap {
    val bmp = android.graphics.Bitmap.createBitmap(240, 180, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.eraseColor(android.graphics.Color.rgb(16, 36, 58))
    return bmp.asImageBitmap()
}
