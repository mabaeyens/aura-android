package com.mab.aura.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mab.aura.MainActivity
import com.mab.aura.core.model.WeatherSnapshot

/**
 * Aura's Home Screen widget. Android port of the iOS `AuraHomeWidget`, moved from the Lock Screen (which
 * Android phones don't have) to the Home Screen per the porting plan.
 *
 * It never calls AEMET: [provideGlance] reads the shared cache ([SharedSnapshot]) and re-renders whatever the
 * app last stored, and the app nudges every widget after each refresh (`WeatherRepository`). Glance renders to
 * RemoteViews, so this draws a restricted layout over a baked sky-gradient bitmap rather than reusing the
 * `AuraSky`/card Composables.
 */
class AuraGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // The cache read (file + DataStore) is suspending, so it happens here, outside the composition; the
        // gradient bitmap is likewise built once per update, not per recomposition.
        val snapshot = SharedSnapshot.resolve(context)
        val background = snapshot?.let { skyGradientBitmap(it.currentSky) }

        provideContent {
            WidgetContent(snapshot, background)
        }
    }
}

private val White = ColorProvider(Color.White)
private val WhiteDim = ColorProvider(Color(0xE6FFFFFF))
private val WhiteFaint = ColorProvider(Color(0xCCFFFFFF))

@Composable
private fun WidgetContent(snapshot: WeatherSnapshot?, background: Bitmap?) {
    // The whole widget opens the app, matching the iOS tap target. actionStartActivity's Intent overload is
    // unambiguous (the reified generic collides with it here), so build the launch Intent explicitly.
    val openApp = actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(16.dp)
            .clickable(openApp),
        contentAlignment = Alignment.Center,
    ) {
        // 1 — the sky gradient, stretched to fill (a fallback flat night colour before the first cache write).
        if (background != null) {
            Image(
                provider = ImageProvider(background),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
        } else {
            Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF12203A)))) {}
        }

        // 2 — a soft dark scrim so white text stays legible over the brightest (clear, day) skies too.
        Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0x40000000)))) {}

        // 3 — the content, or the empty invitation before anything is cached.
        if (snapshot == null) EmptyContent() else FilledContent(snapshot)
    }
}

@Composable
private fun FilledContent(snapshot: WeatherSnapshot) {
    val temp = snapshot.heroTemp?.let { "$it°" } ?: "--°"
    val hiLo = listOfNotNull(
        snapshot.tempMax?.let { "Máx $it°" },
        snapshot.tempMin?.let { "Mín $it°" },
    ).joinToString(" · ")
    val precip = snapshot.upcomingHours().firstOrNull()?.precipProb

    Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
        Text(
            text = snapshot.localidad,
            style = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            text = temp,
            style = TextStyle(color = White, fontSize = 40.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        snapshot.currentSkyText?.let {
            Text(
                text = it,
                style = TextStyle(color = WhiteDim, fontSize = 13.sp),
                maxLines = 1,
            )
        }
        if (hiLo.isNotEmpty()) {
            Text(
                text = hiLo,
                style = TextStyle(color = WhiteFaint, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        precip?.let {
            Text(
                text = "Lluvia $it%",
                style = TextStyle(color = WhiteFaint, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Aura",
            style = TextStyle(color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = "Abre la app para ver el tiempo",
            style = TextStyle(color = WhiteFaint, fontSize = 12.sp, textAlign = TextAlign.Center),
        )
    }
}
