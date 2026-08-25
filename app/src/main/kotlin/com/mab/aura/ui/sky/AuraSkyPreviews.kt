package com.mab.aura.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.ui.PhasedMoonDisc
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * `@Preview`s for the sky primitives, so each renders in Android Studio's preview pane without running the
 * app. These are development-only scaffolding (the tooling strips them from a real screen); the sample data
 * builders below pin a fixed day and clock time so a preview always shows the same sky.
 *
 * `AuraSky` reads only `currentSky`, `sunrise` and `sunset` from the snapshot, so the samples fill just
 * those (plus the four non-optional identity fields).
 */

private val PREVIEW_DAY: LocalDate = LocalDate.of(2026, 6, 21)

/** A sample snapshot for a given AEMET sky code, with sunrise ~06:45 and sunset ~21:30 local. */
private fun previewSnapshot(sky: String): WeatherSnapshot {
    val zone = ZoneId.systemDefault()
    fun at(hour: Int, minute: Int): Instant = PREVIEW_DAY.atTime(hour, minute).atZone(zone).toInstant()
    return WeatherSnapshot(
        ine = "28079",
        localidad = "Madrid",
        provincia = "Madrid",
        currentSky = sky,
        sunrise = at(6, 45),
        sunset = at(21, 30),
        updated = at(12, 0),
    )
}

/** A fixed instant at [hour]:[minute] local on the preview day. */
private fun previewNow(hour: Int, minute: Int = 0): Instant =
    PREVIEW_DAY.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()

@Preview(name = "Sky · clear midday", widthDp = 320, heightDp = 680)
@Composable
private fun AuraSkyClearMiddayPreview() {
    AuraSky(snapshot = previewSnapshot("11"), now = previewNow(13), modifier = Modifier.fillMaxSize())
}

@Preview(name = "Sky · few clouds dawn", widthDp = 320, heightDp = 680)
@Composable
private fun AuraSkyDawnPreview() {
    AuraSky(snapshot = previewSnapshot("12"), now = previewNow(7, 30), modifier = Modifier.fillMaxSize())
}

@Preview(name = "Sky · dusk", widthDp = 320, heightDp = 680)
@Composable
private fun AuraSkyDuskPreview() {
    AuraSky(snapshot = previewSnapshot("11"), now = previewNow(20, 45), modifier = Modifier.fillMaxSize())
}

@Preview(name = "Sky · clear night", widthDp = 320, heightDp = 680)
@Composable
private fun AuraSkyClearNightPreview() {
    AuraSky(snapshot = previewSnapshot("11n"), now = previewNow(1), modifier = Modifier.fillMaxSize())
}

@Preview(name = "Sky · rain", widthDp = 320, heightDp = 680)
@Composable
private fun AuraSkyRainPreview() {
    AuraSky(snapshot = previewSnapshot("23"), now = previewNow(15), modifier = Modifier.fillMaxSize())
}

@Preview(name = "Sky · overcast", widthDp = 320, heightDp = 680)
@Composable
private fun AuraSkyOvercastPreview() {
    AuraSky(snapshot = previewSnapshot("16"), now = previewNow(11), modifier = Modifier.fillMaxSize())
}

@Preview(name = "PhasedMoonDisc · phases", widthDp = 320, heightDp = 140, backgroundColor = 0xFF0B1020)
@Composable
private fun PhasedMoonDiscPreview() {
    // New → crescent → half → gibbous → full, drawn against a night-sky background.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1020))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (illum in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            Box {
                PhasedMoonDisc(illumination = illum, waxing = true, radius = 16.dp)
            }
        }
    }
}
