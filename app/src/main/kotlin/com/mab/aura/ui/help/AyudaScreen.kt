package com.mab.aura.ui.help

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mab.aura.R
import com.mab.aura.ui.AnimatedConditionGlyph
import com.mab.aura.ui.theme.Palette

/**
 * "Ayuda" — the reference screen ported from `HelpView.swift`. It does two jobs: explain how to get a free
 * AEMET key, and give a legend for the app's icons and cards. Reached from the hero gear menu, next to
 * Acerca de (see [com.mab.aura.ui.hoy.HoyScreen]).
 *
 * The legend mirrors the iOS screen section for section, each metric with its own glyph. Where iOS draws an
 * SF Symbol, Android draws the Meteocons equivalent — the same colourful glyphs the cards use, so the legend
 * can't drift from what the forecast shows: sunrise/sunset, umbrella, humidity, wind, and the numbered UV
 * badges all come from `res/drawable/ic_wx_*`. The few genuinely plain iOS symbols (temperatura máx/mín arrows,
 * the aviso triangle) use `ic_arrow_*` and `material-icons-core`'s Warning; air quality shows its ICA colour
 * swatch, as the card does. The sky-condition legend plays the real [AnimatedConditionGlyph]. No heavy
 * `material-icons-extended` dependency is pulled in for any of it. The colour scales aren't repeated here: each
 * card opens its own on a tap, and this points there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyudaScreen(onBack: () -> Unit, onOpenFreshness: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun openApiKeyPage() {
        val url = "https://opendata.aemet.es/centrodedescargas/altaUsuario"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            ApiKeySection(onRequestKey = ::openApiKeyPage)
            // Tappable row that opens the data-freshness page. Sits right under the API-key how-to, matching
            // where iOS links it from its Help screen.
            FreshnessLink(onOpen = onOpenFreshness)
            SkySection()
            TemperatureSection()
            RainHumiditySection()
            WindSection()
            SunMoonSection()
            UVSection()
            AirSection()
            AvisoSection()
            AppSection()
            ScalesSection()
        }
    }
}

/**
 * "Actualización de los datos" — the data-freshness sub-page, pushed from [AyudaScreen]. It explains where
 * each reading comes from and how often it refreshes, so the mix of cadences (a station reading published
 * 20-40 min after the hour, an hourly forecast, a twice-daily bulletin, a 10-min radar) never reads as the
 * app being out of date. Kept in step with the iOS "Data freshness" page; the stated cadences mirror the real
 * constants in the code (see [com.mab.aura.data.WeatherRepository], `RadarRepository`, `NewsService`, and the
 * one-minute pull-to-refresh cooldown in [com.mab.aura.ui.hoy.HoyViewModel]). Pure copy: it fetches nothing.
 *
 * It reuses this file's [Section] and [Body] blocks so it looks like the rest of Ayuda; each source is a plain
 * paragraph rather than a titled row, because every string already names what it describes ("La cabecera…",
 * "La previsión municipal…"), matching how iOS lays it out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreshnessScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_freshness_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Body(stringResource(R.string.help_freshness_intro))
            Section(header = stringResource(R.string.help_freshness_data_header)) {
                Body(stringResource(R.string.help_freshness_observed))
                Body(stringResource(R.string.help_freshness_forecast))
                Body(stringResource(R.string.help_freshness_bulletin))
                Body(stringResource(R.string.help_freshness_radar))
                Body(stringResource(R.string.help_freshness_uv))
                Body(stringResource(R.string.help_freshness_air))
                Body(stringResource(R.string.help_freshness_aviso))
                Body(stringResource(R.string.help_freshness_news))
            }
            Section(header = stringResource(R.string.help_freshness_app_header)) {
                Body(stringResource(R.string.help_freshness_refresh))
            }
        }
    }
}

// MARK: Sections

/** The tappable row in [AyudaScreen] that opens [FreshnessScreen]: a title, a one-line teaser and a chevron,
 *  in a tonal surface so it reads as a link rather than a legend row. */
@Composable
private fun FreshnessLink(onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.help_freshness_link_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.help_freshness_link_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiKeySection(onRequestKey: () -> Unit) {
    Section(
        header = stringResource(R.string.help_apikey_header),
        footer = stringResource(R.string.help_apikey_footer),
    ) {
        Body(stringResource(R.string.help_apikey_body))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            Step(1, stringResource(R.string.help_apikey_step1))
            Step(2, stringResource(R.string.help_apikey_step2))
            Step(3, stringResource(R.string.help_apikey_step3))
            Step(4, stringResource(R.string.help_apikey_step4))
        }
        FilledTonalButton(onClick = onRequestKey) {
            Text(stringResource(R.string.help_apikey_button))
        }
    }
}

@Composable
private fun SkySection() {
    Section(
        header = stringResource(R.string.help_sky_header),
        footer = stringResource(R.string.help_sky_footer),
    ) {
        ConditionRow("11", night = false, stringResource(R.string.help_sky_clear))
        ConditionRow("11", night = true, stringResource(R.string.help_sky_clear_night))
        ConditionRow("12", night = false, stringResource(R.string.help_sky_partly))
        ConditionRow("14", night = false, stringResource(R.string.help_sky_cloudy))
        ConditionRow("16", night = false, stringResource(R.string.help_sky_fog))
        ConditionRow("23", night = false, stringResource(R.string.help_sky_showers))
        ConditionRow("24", night = false, stringResource(R.string.help_sky_rain))
        ConditionRow("25", night = false, stringResource(R.string.help_sky_heavy_rain))
        ConditionRow("33", night = false, stringResource(R.string.help_sky_snow))
        ConditionRow("71", night = false, stringResource(R.string.help_sky_sleet))
        ConditionRow("51", night = false, stringResource(R.string.help_sky_storm))
        ConditionRow("53", night = false, stringResource(R.string.help_sky_storm_rain))
    }
}

@Composable
private fun TemperatureSection() {
    Section(
        header = stringResource(R.string.help_temp_header),
        footer = stringResource(R.string.help_temp_footer),
    ) {
        TintRow(R.drawable.ic_arrow_up, stringResource(R.string.help_temp_max_title), stringResource(R.string.help_temp_max_meaning))
        TintRow(R.drawable.ic_arrow_down, stringResource(R.string.help_temp_min_title), stringResource(R.string.help_temp_min_meaning))
    }
}

@Composable
private fun RainHumiditySection() {
    Section(
        header = stringResource(R.string.help_rain_header),
        footer = stringResource(R.string.help_rain_footer),
    ) {
        GlyphRow(R.drawable.ic_wx_umbrella, stringResource(R.string.help_rain_prob_title), stringResource(R.string.help_rain_prob_meaning))
        GlyphRow(R.drawable.ic_wx_humidity, stringResource(R.string.help_rain_humidity_title), stringResource(R.string.help_rain_humidity_meaning))
    }
}

@Composable
private fun WindSection() {
    Section(
        header = stringResource(R.string.help_wind_header),
        footer = stringResource(R.string.help_wind_footer),
    ) {
        GlyphRow(R.drawable.ic_wx_wind, stringResource(R.string.help_wind_speed_title), stringResource(R.string.help_wind_speed_meaning))
        // The wind rose is a custom compass mark on the card, not a Meteocons glyph; the tinted arrow stands in
        // for it here, teal like the card's pointer, as iOS uses a teal north arrow in this same row.
        TintRow(R.drawable.ic_arrow_up, stringResource(R.string.help_wind_rose_title), stringResource(R.string.help_wind_rose_meaning), tint = Palette.tempTeal)
    }
}

@Composable
private fun SunMoonSection() {
    Section(header = stringResource(R.string.help_sun_header)) {
        GlyphRow(R.drawable.ic_wx_sunrise, stringResource(R.string.help_sun_sunrise_title), stringResource(R.string.help_sun_sunrise_meaning))
        GlyphRow(R.drawable.ic_wx_sunset, stringResource(R.string.help_sun_sunset_title), stringResource(R.string.help_sun_sunset_meaning))
        ConditionRow("11", night = true, stringResource(R.string.help_sun_night))
    }
}

@Composable
private fun UVSection() {
    Section(
        header = stringResource(R.string.help_uv_header),
        footer = stringResource(R.string.help_uv_footer),
    ) {
        GlyphRow(R.drawable.ic_wx_uv_2, stringResource(R.string.help_uv_low_title), stringResource(R.string.help_uv_low_meaning))
        GlyphRow(R.drawable.ic_wx_uv_4, stringResource(R.string.help_uv_moderate_title), stringResource(R.string.help_uv_moderate_meaning))
        GlyphRow(R.drawable.ic_wx_uv_7, stringResource(R.string.help_uv_high_title), stringResource(R.string.help_uv_high_meaning))
        GlyphRow(R.drawable.ic_wx_uv_9, stringResource(R.string.help_uv_veryhigh_title), stringResource(R.string.help_uv_veryhigh_meaning))
        GlyphRow(R.drawable.ic_wx_uv_11, stringResource(R.string.help_uv_extreme_title), stringResource(R.string.help_uv_extreme_meaning))
        GlyphRow(R.drawable.ic_wx_cloudy, stringResource(R.string.help_uv_clouds_title), stringResource(R.string.help_uv_clouds_meaning))
    }
}

@Composable
private fun AirSection() {
    Section(
        header = stringResource(R.string.help_air_header),
        footer = stringResource(R.string.help_air_footer),
    ) {
        // The card shows the ICA level as a coloured swatch, not an icon; the legend uses the same swatch. A
        // mid "Regular" band (3) is representative of the six-colour scale.
        SwatchRow(Palette.airQuality(3), stringResource(R.string.help_air_title), stringResource(R.string.help_air_meaning))
    }
}

@Composable
private fun AvisoSection() {
    Section(header = stringResource(R.string.help_aviso_header)) {
        IconRow(Icons.Filled.Warning, stringResource(R.string.help_aviso_title), stringResource(R.string.help_aviso_meaning))
    }
}

@Composable
private fun AppSection() {
    Section(header = stringResource(R.string.help_app_header)) {
        IconRow(Icons.Filled.Settings, stringResource(R.string.help_app_menu_title), stringResource(R.string.help_app_menu_meaning))
        IconRow(Icons.Filled.Place, stringResource(R.string.help_app_locations_title), stringResource(R.string.help_app_locations_meaning))
        IconRow(Icons.Filled.Info, stringResource(R.string.help_app_about_title), stringResource(R.string.help_app_about_meaning))
        IconRow(Icons.Filled.Search, stringResource(R.string.help_app_search_title), stringResource(R.string.help_app_search_meaning))
        IconRow(Icons.Filled.Add, stringResource(R.string.help_app_add_title), stringResource(R.string.help_app_add_meaning))
        IconRow(Icons.Filled.Check, stringResource(R.string.help_app_chosen_title), stringResource(R.string.help_app_chosen_meaning))
        IconRow(Icons.Filled.KeyboardArrowDown, stringResource(R.string.help_app_expand_title), stringResource(R.string.help_app_expand_meaning))
        IconRow(Icons.Filled.Close, stringResource(R.string.action_close), stringResource(R.string.help_app_close_meaning))
    }
}

@Composable
private fun ScalesSection() {
    Section(
        header = stringResource(R.string.help_scales_header),
        footer = stringResource(R.string.help_scales_footer),
    ) {
        Body(stringResource(R.string.help_scales_body))
    }
}

// MARK: Row + section building blocks

/** A titled group with an optional footer note, echoing the iOS `Form` section (header, rows, footer). */
@Composable
private fun Section(header: String, footer: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

/** A numbered step in the API-key how-to, with a filled circle badge like the iOS accent circle. */
@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$n",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A legend row whose glyph is a weather condition, drawn by the same [AnimatedConditionGlyph] the cards use
 *  so the legend can't drift. Each rides a small day/night sky tile, since Aura's clouds are near-white and
 *  would vanish on the plain surface. */
@Composable
private fun ConditionRow(code: String, night: Boolean, title: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(skyTile(night)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedConditionGlyph(sky = code, isNight = night, modifier = Modifier.size(22.dp))
        }
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A legend row with a core Material icon, a name, and a one-line meaning. */
@Composable
private fun IconRow(icon: ImageVector, title: String, meaning: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = meaning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A legend row led by a colourful Meteocons glyph (`ic_wx_*`), drawn untinted so it keeps its own colours —
 *  the counterpart to iOS's `.multicolor` SF Symbols. Used for the weather metrics: sunrise, umbrella, UV, … */
@Composable
private fun GlyphRow(@DrawableRes icon: Int, title: String, meaning: String) {
    LegendRow(
        leading = {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        },
        title = title,
        meaning = meaning,
    )
}

/** A legend row led by a monochrome drawable tinted to a flat colour — the plain iOS symbols that carry meaning
 *  through shape, not colour (the temperatura arrows), or a single accent (the teal wind-rose arrow). */
@Composable
private fun TintRow(
    @DrawableRes icon: Int,
    title: String,
    meaning: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    LegendRow(
        leading = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        },
        title = title,
        meaning = meaning,
    )
}

/** A legend row led by a filled colour circle, matching the air-quality card's ICA swatch. */
@Composable
private fun SwatchRow(color: Color, title: String, meaning: String) {
    LegendRow(
        leading = {
            Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(color))
        },
        title = title,
        meaning = meaning,
    )
}

/** The shared shell for a legend row: a leading glyph in a fixed-width gutter, then the title over its meaning. */
@Composable
private fun LegendRow(leading: @Composable () -> Unit, title: String, meaning: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading() }
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = meaning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A little day or night sky behind a condition glyph, matching the gradient the iOS Help screen uses so a
 *  white cloud or a blue moon reads the way it does over the app's own sky. */
private fun skyTile(night: Boolean): Brush = Brush.verticalGradient(
    if (night) {
        listOf(Color(red = 0.10f, green = 0.14f, blue = 0.30f), Color(red = 0.22f, green = 0.28f, blue = 0.46f))
    } else {
        listOf(Color(red = 0.26f, green = 0.52f, blue = 0.86f), Color(red = 0.55f, green = 0.75f, blue = 0.96f))
    },
)
