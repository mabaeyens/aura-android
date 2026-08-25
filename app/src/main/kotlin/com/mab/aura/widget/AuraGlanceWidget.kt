package com.mab.aura.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mab.aura.MainActivity
import com.mab.aura.core.icon.WeatherIcon
import com.mab.aura.core.model.HourSlot
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.time.AuraTime
import com.mab.aura.store.Settings
import com.mab.aura.ui.drawableFor
import kotlinx.coroutines.flow.first

/**
 * Aura's Home Screen widget. Android port of the iOS `AuraHomeWidget`, moved from the Lock Screen (which
 * Android phones don't have) to the Home Screen per the porting plan.
 *
 * It never calls AEMET: [provideGlance] reads the shared cache ([SharedSnapshot]) and re-renders whatever the
 * app last stored, and the app nudges every widget after each refresh (`WeatherRepository`). Glance renders to
 * RemoteViews, so this draws a restricted layout over a baked sky-gradient bitmap rather than reusing the
 * `AuraSky`/card Composables — but it does reuse the app's bundled condition drawables for the hourly strip.
 */
class AuraGlanceWidget : GlanceAppWidget() {

    // Exact so the content re-renders with the widget's real size on every resize; the layout then adds the
    // "Próximas horas" strip once the tile is tall enough for it (see [FilledContent]).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Each tile can pin its own place (WidgetConfigActivity writes the INE into this widget's own Glance
        // state, keyed per GlanceId). A tile that was never configured has no pin, so it follows the app's
        // active location — that fallback lives in SharedSnapshot.resolve, which treats a null/stale pin as
        // "use the app's active location, then the first cache entry".
        val pinnedINE = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[PINNED_INE_KEY]

        // The cache read (file + DataStore) is suspending, so it happens here, outside the composition; the
        // gradient bitmap is likewise built once per update, not per recomposition.
        val snapshot = SharedSnapshot.resolve(context, pinnedINE)
        val use24h = Settings(context).use24h.first()
        val background = snapshot?.let { skyGradientBitmap(it.currentSky) }

        provideContent {
            WidgetContent(snapshot, background, use24h)
        }
    }
}

/**
 * The per-widget pinned INE, stored in each tile's own Glance preferences (the default
 * [PreferencesGlanceStateDefinition]). Written by [WidgetConfigActivity], read in [AuraGlanceWidget.provideGlance].
 * Absent means "follow the app's active location".
 */
internal val PINNED_INE_KEY = stringPreferencesKey("pinned_ine")

private val White = ColorProvider(Color.White)
private val WhiteDim = ColorProvider(Color(0xE6FFFFFF))
private val WhiteFaint = ColorProvider(Color(0xCCFFFFFF))

// The tile is tall enough to carry the hourly strip below the summary from about here up (a 2x2 tile is
// ~110 dp and stays compact; a taller/medium tile gets the strip). Set with headroom so the strip's last
// row never clips at the boundary.
private val HOURS_MIN_HEIGHT = 190.dp

@Composable
private fun WidgetContent(snapshot: WeatherSnapshot?, background: Bitmap?, use24h: Boolean) {
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
        if (snapshot == null) EmptyContent() else FilledContent(snapshot, use24h)
    }
}

@Composable
private fun FilledContent(snapshot: WeatherSnapshot, use24h: Boolean) {
    val temp = snapshot.heroTemp?.let { "$it°" } ?: "--°"
    val hiLo = listOfNotNull(
        snapshot.tempMax?.let { "Máx $it°" },
        snapshot.tempMin?.let { "Mín $it°" },
    ).joinToString(" · ")
    val hours = snapshot.upcomingHours().take(4)
    val showHours = LocalSize.current.height >= HOURS_MIN_HEIGHT && hours.isNotEmpty()

    Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
        Text(
            text = snapshot.localidad,
            style = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            text = temp,
            style = TextStyle(color = White, fontSize = 38.sp, fontWeight = FontWeight.Medium),
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

        if (showHours) {
            // Push the strip to the bottom of the tile so it fills the space instead of leaving a gap.
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "PRÓXIMAS HORAS",
                style = TextStyle(color = WhiteFaint, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))
            // Only carry a rain row at all when some hour is actually wet — a strip of "0%" is noise, and
            // dropping it keeps the strip a row shorter so it fits without clipping on a dry day.
            val anyRain = hours.any { (it.precipProb ?: 0) > 0 }
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                // defaultWeight() is a RowScope member, so the equal-share weight is applied here at the call
                // site (inside the Row) and passed into each column, not from inside HourColumn.
                hours.forEach { slot -> HourColumn(slot, use24h, anyRain, GlanceModifier.defaultWeight()) }
            }
        } else {
            // Compact tile: no room for the strip, so keep the current hour's rain on its own line.
            snapshot.upcomingHours().firstOrNull()?.precipProb?.let {
                Text(
                    text = "Lluvia $it%",
                    style = TextStyle(color = WhiteFaint, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** One column of the hourly strip: the hour, the condition icon (the app's own drawable), the temperature,
 *  and the rain probability when there is one. Each takes an equal share of the row via [defaultWeight]. */
@Composable
private fun HourColumn(slot: HourSlot, use24h: Boolean, showRain: Boolean, modifier: GlanceModifier) {
    val glyph = WeatherIcon.glyph(slot.sky)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = AuraTime.hourLabel(slot.hour, use24h),
            style = TextStyle(color = WhiteFaint, fontSize = 12.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(3.dp))
        Image(
            provider = ImageProvider(drawableFor(glyph)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(White),
            modifier = GlanceModifier.size(20.dp),
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = slot.temp?.let { "$it°" } ?: "--°",
            style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        // Rendered across all columns or none (see anyRain), so the columns stay aligned; a dry hour in a
        // wet strip shows a blank placeholder rather than collapsing its column shorter than the others.
        if (showRain) {
            Text(
                text = slot.precipProb?.takeIf { it > 0 }?.let { "$it%" } ?: " ",
                style = TextStyle(color = WhiteFaint, fontSize = 11.sp),
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
