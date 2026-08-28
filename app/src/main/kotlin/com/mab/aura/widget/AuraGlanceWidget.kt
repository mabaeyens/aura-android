package com.mab.aura.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mab.aura.MainActivity
import com.mab.aura.R
import com.mab.aura.core.hero.HeroBackground
import com.mab.aura.core.icon.WeatherIcon
import com.mab.aura.core.model.HourSlot
import com.mab.aura.core.model.WeatherAlert
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.store.Settings
import com.mab.aura.ui.drawableFor
import com.mab.aura.ui.theme.Palette
import kotlinx.coroutines.flow.first
import java.time.Instant

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

        // The cache read (file + DataStore) and asset decode are suspending/blocking, so they happen here,
        // outside the composition, once per update rather than per recomposition. The background is the real
        // wide hero scene for this sky + time of day, falling back to the procedural sky gradient.
        val settings = Settings(context)
        val snapshot = SharedSnapshot.resolve(context, pinnedINE)
        val now = Instant.now()
        val family = HeroBackground.Family.from(settings.heroFamily.first())
        val hero = snapshot?.let { wideHeroBitmap(context, it, now, family) }
        val background = hero ?: snapshot?.let { skyGradientBitmap(it.currentSky) }

        provideContent {
            WidgetContent(snapshot, background, backgroundIsHero = hero != null, now = now)
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

@Composable
private fun WidgetContent(
    snapshot: WeatherSnapshot?,
    background: Bitmap?,
    backgroundIsHero: Boolean,
    now: Instant,
) {
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
        // 1 — the background: the wide hero scene, centre-cropped to the tile so it keeps its aspect (the art
        // is 4:3), or the thin procedural gradient stretched to fill. A flat night colour shows before the
        // first cache write.
        if (background != null) {
            val provider = if (backgroundIsHero) {
                // The sky trim is per tile. A wide/short tile (e.g. 4x2) would centre-crop to a thin, mostly
                // sky band, so trim the top sky first (SKY_CUT_WIDE) to bring the scene up; a tall tile (e.g.
                // 2x4) already shows plenty of scene under a centre-crop, so it gets no trim. Square tiles fall
                // in with the wide case. LocalSize is the real tile size here (SizeMode.Exact).
                val size = LocalSize.current
                val cut = if (size.height > size.width) 0f else SKY_CUT_WIDE
                ImageProvider(cropTopSky(background, cut))
            } else {
                ImageProvider(background)
            }
            Image(
                provider = provider,
                contentDescription = null,
                contentScale = if (backgroundIsHero) ContentScale.Crop else ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
        } else {
            Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF12203A)))) {}
        }

        // 2 — a soft dark scrim so white text stays legible over the brightest (clear, day) skies too. A hero
        // scene's bright noon sky needs a touch more than the flat gradient did.
        val scrim = if (backgroundIsHero) 0x59000000 else 0x40000000
        Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(scrim)))) {}

        // 3 — the content, or the empty invitation before anything is cached.
        if (snapshot == null) EmptyContent() else FilledContent(snapshot, now)
    }
}

/**
 * The filled widget, laid out to match the iOS Home Screen widget:
 *
 *  - a **wide/medium** tile (a 4x2 and up) puts the condition block on the left and a four-hour strip on
 *    the right, side by side, the way `AuraHomeMedium` does on iOS;
 *  - a **compact** 2x2 tile keeps just the condition block, sat against the bottom like `AuraHomeSmall`.
 *
 * The height guard keeps the very short 4x1 tile — too shallow for the tall block beside a strip — on the
 * compact path rather than clipping the temperature.
 */
@Composable
private fun FilledContent(snapshot: WeatherSnapshot, now: Instant) {
    val size = LocalSize.current
    val medium = size.width >= 220.dp && size.height >= 120.dp
    val alert = snapshot.activeAlert(now)

    Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
        LocationRow(snapshot.localidad, alert)
        Spacer(GlanceModifier.height(6.dp))
        if (medium) {
            // Block left, four-hour strip right (a fifth column crowds this width, as on iOS). The block sits
            // at the top under the location row; the strip is centred vertically in the tile so it reads as
            // its own band down the middle rather than clinging to the top edge.
            Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
                ConditionBlock(snapshot, now, medium = true)
                Spacer(GlanceModifier.width(10.dp))
                Box(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        snapshot.upcomingHours(now).take(4).forEach { slot ->
                            HourColumn(slot, GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        } else {
            // Compact (narrow) tile. The block sits low like the iOS small widget; a four-hour strip is added
            // beneath it only when the tile is tall enough to carry it at the same readable font the wide tile
            // uses. A true 2x2 has that room, so it gets the strip; a short 2x1 doesn't, so it keeps just the
            // block rather than a cramped, shrunken strip. When the strip shows, a second weighted spacer
            // centres the block-and-strip group in the space below the location row.
            val showStrip = size.height >= 180.dp
            Spacer(GlanceModifier.defaultWeight())
            ConditionBlock(snapshot, now, medium = false)
            if (showStrip) {
                Spacer(GlanceModifier.height(10.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    snapshot.upcomingHours(now).take(4).forEach { slot ->
                        HourColumn(slot, GlanceModifier.defaultWeight())
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

/** The place name behind a location pin, with the aviso pill pushed to the trailing edge when a warning is
 *  active — the iOS `HomeLocationRow`. The pin is the widget's own white vector (Glance can't reach the
 *  app's Compose [androidx.compose.material.icons.Icons]). */
@Composable
private fun LocationRow(place: String, alert: WeatherAlert?) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_location),
            contentDescription = null,
            modifier = GlanceModifier.size(13.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = place,
            style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        if (alert != null) AvisoPill(alert.level)
    }
}

/** The current condition glyph beside the hero temperature, the sky phrase, and today's high/low as up/down
 *  arrows — the iOS `HomeConditionBlock`. [medium] shrinks the glyph and temperature a step and lets the sky
 *  phrase wrap to two lines, keeping the left block narrow so the hour strip beside it has room. */
@Composable
private fun ConditionBlock(snapshot: WeatherSnapshot, now: Instant, medium: Boolean) {
    val glyph = WeatherIcon.glyph(snapshot.currentSky, snapshot.isNight(now))
    val temp = snapshot.heroTemp(now)?.let { "$it°" } ?: "--°"
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // No colorFilter: the Meteocons drawables are full-colour (yellow sun, blue rain), so tinting
            // would flatten them to a white silhouette. RemoteViews can't animate, so the widget shows the
            // static Meteocons while the in-app strip plays the matching Lottie.
            // Sized larger than the nominal glyph for the same reason the in-app cards are (Meteocons fill
            // only ~75% of their canvas, so a literal size reads small); bumped a step to match iOS's denser
            // symbol. Still shorter than the temperature line beside it, so the row height is unchanged.
            Image(
                provider = ImageProvider(drawableFor(glyph)),
                contentDescription = null,
                modifier = GlanceModifier.size(if (medium) 44.dp else 46.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = temp,
                style = TextStyle(color = White, fontSize = if (medium) 34.sp else 38.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        snapshot.currentSkyText?.let {
            Text(
                text = it,
                style = TextStyle(color = WhiteDim, fontSize = 13.sp),
                maxLines = if (medium) 2 else 1,
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        HighLow(snapshot)
    }
}

/** Today's high and low as "↑ 33°  ↓ 23°", matching the iOS block's arrow.up / arrow.down labels (the arrows
 *  are Unicode glyphs, so no drawable is needed). Hidden entirely when neither value is known. */
@Composable
private fun HighLow(snapshot: WeatherSnapshot) {
    val hi = snapshot.tempMax?.let { "↑ $it°" }
    val lo = snapshot.tempMin?.let { "↓ $it°" }
    if (hi == null && lo == null) return
    val style = TextStyle(color = WhiteFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        hi?.let { Text(text = it, style = style, maxLines = 1) }
        if (hi != null && lo != null) Spacer(GlanceModifier.width(10.dp))
        lo?.let { Text(text = it, style = style, maxLines = 1) }
    }
}

/** A small level-tinted "Aviso" pill for an active AEMET warning, the iOS `AvisoPill`. The warning triangle
 *  and colour follow the app's own alert palette ([Palette.alert]). */
@Composable
private fun AvisoPill(level: WeatherAlert.Level) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .background(ColorProvider(Palette.alert(level)))
            .cornerRadius(10.dp)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_warning),
            contentDescription = null,
            modifier = GlanceModifier.size(11.dp),
        )
        Spacer(GlanceModifier.width(3.dp))
        Text(
            text = context.getString(R.string.widget_content_aviso),
            style = TextStyle(color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** One column of the hourly strip: the bare hour number, the condition glyph, the temperature — the iOS
 *  `HomeHourColumn`. iOS shows the raw 24h hour here regardless of the app's clock setting, and carries no
 *  rain row on the medium strip, so this matches both. Each column takes an equal share via [defaultWeight]. */
@Composable
private fun HourColumn(slot: HourSlot, modifier: GlanceModifier) {
    val glyph = WeatherIcon.glyph(slot.sky)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${slot.hour}",
            style = TextStyle(color = WhiteFaint, fontSize = 12.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(3.dp))
        // Bumped from the nominal size to match the in-app strip: the Meteocons' own ~25% padding otherwise
        // makes a literal size read small. Still fits the weighted four-column strip on a medium/2x2 tile.
        Image(
            provider = ImageProvider(drawableFor(glyph)),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = slot.temp?.let { "$it°" } ?: "--°",
            style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyContent() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_content_empty_title),
            style = TextStyle(color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = context.getString(R.string.widget_content_empty_message),
            style = TextStyle(color = WhiteFaint, fontSize = 12.sp, textAlign = TextAlign.Center),
        )
    }
}
