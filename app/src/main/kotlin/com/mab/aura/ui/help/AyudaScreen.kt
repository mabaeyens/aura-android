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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
fun AyudaScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun openApiKeyPage() {
        val url = "https://opendata.aemet.es/centrodedescargas/altaUsuario"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ayuda") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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

// MARK: Sections

@Composable
private fun ApiKeySection(onRequestKey: () -> Unit) {
    Section(
        header = "Tu clave de AEMET",
        footer = "Si la previsión deja de actualizarse, pide otra clave del mismo modo y vuelve a pegarla.",
    ) {
        Body(
            "Aura muestra la previsión de AEMET, que es pública y gratuita. Para usarla necesitas tu propia " +
                "clave, también gratis y personal.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            Step(1, "Abre la página de AEMET (botón de abajo).")
            Step(2, "Escribe tu correo y acepta las condiciones.")
            Step(3, "AEMET te envía la clave por correo (mira también en spam).")
            Step(4, "Pégala en Ajustes → Clave API.")
        }
        FilledTonalButton(onClick = onRequestKey) {
            Text("Solicitar mi clave en AEMET")
        }
    }
}

@Composable
private fun SkySection() {
    Section(
        header = "Condiciones del cielo",
        footer = "De noche, el sol de estos iconos se convierte en luna.",
    ) {
        ConditionRow("11", night = false, "Despejado")
        ConditionRow("11", night = true, "Despejado de noche")
        ConditionRow("12", night = false, "Poco nuboso o con intervalos")
        ConditionRow("14", night = false, "Nuboso o cubierto")
        ConditionRow("16", night = false, "Niebla o bruma")
        ConditionRow("23", night = false, "Lluvia con claros / chubascos")
        ConditionRow("24", night = false, "Lluvia")
        ConditionRow("25", night = false, "Lluvia fuerte")
        ConditionRow("33", night = false, "Nieve")
        ConditionRow("71", night = false, "Nieve escasa (aguanieve)")
        ConditionRow("51", night = false, "Tormenta")
        ConditionRow("53", night = false, "Tormenta con lluvia")
    }
}

@Composable
private fun TemperatureSection() {
    Section(
        header = "Temperatura",
        footer = "Los grados se colorean en una escala continua de azul (frío) a rojo (calor): la misma en las tarjetas, las barras de rango y el widget.",
    ) {
        TintRow(R.drawable.ic_arrow_up, "Temperatura máxima", "La más alta prevista para el día.")
        TintRow(R.drawable.ic_arrow_down, "Temperatura mínima", "La más baja prevista para el día.")
    }
}

@Composable
private fun RainHumiditySection() {
    Section(
        header = "Lluvia y humedad",
        footer = "El paraguas (lluvia) y la gota con ondas (humedad) son cosas distintas a propósito.",
    ) {
        GlyphRow(R.drawable.ic_wx_umbrella, "Probabilidad de lluvia", "El porcentaje de que llueva. Es la lluvia.")
        GlyphRow(R.drawable.ic_wx_humidity, "Humedad relativa", "El agua que hay en el aire, en %. No es la lluvia.")
    }
}

@Composable
private fun WindSection() {
    Section(
        header = "Viento",
        footer = "Toca la tarjeta del viento para ver la escala Beaufort completa.",
    ) {
        GlyphRow(R.drawable.ic_wx_wind, "Velocidad del viento", "La velocidad en km/h, la unidad que da AEMET.")
        // The wind rose is a custom compass mark on the card, not a Meteocons glyph; the tinted arrow stands in
        // for it here, teal like the card's pointer, as iOS uses a teal north arrow in this same row.
        TintRow(R.drawable.ic_arrow_up, "Rosa de los vientos", "La flecha señala la dirección del viento; su color, la intensidad.", tint = Palette.tempTeal)
    }
}

@Composable
private fun SunMoonSection() {
    Section(header = "Sol y luna") {
        GlyphRow(R.drawable.ic_wx_sunrise, "Amanecer", "La salida (orto) del sol.")
        GlyphRow(R.drawable.ic_wx_sunset, "Atardecer", "La puesta (ocaso) del sol.")
        ConditionRow("11", night = true, "De noche, el sol de los iconos se convierte en luna.")
    }
}

@Composable
private fun UVSection() {
    Section(
        header = "Índice UV",
        footer = "Es el máximo previsto del día con el cielo despejado. Toca la tarjeta para ver la escala completa.",
    ) {
        GlyphRow(R.drawable.ic_wx_uv_2, "UV bajo (0–2)", "Sin protección necesaria.")
        GlyphRow(R.drawable.ic_wx_uv_4, "UV moderado (3–5)", "Gafas de sol y crema.")
        GlyphRow(R.drawable.ic_wx_uv_7, "UV alto (6–7)", "Protección recomendada.")
        GlyphRow(R.drawable.ic_wx_uv_9, "UV muy alto (8–10)", "Evita el sol del mediodía.")
        GlyphRow(R.drawable.ic_wx_uv_11, "UV extremadamente alto (11+)", "Evita la exposición al sol.")
        GlyphRow(R.drawable.ic_wx_cloudy, "UV atenuado por nubes", "El cielo nublado puede bajar el UV de ahora por debajo del máximo.")
    }
}

@Composable
private fun AirSection() {
    Section(
        header = "Calidad del aire",
        footer = "Toca la tarjeta para ver la escala completa y cada contaminante por separado.",
    ) {
        // The card shows the ICA level as a coloured swatch, not an icon; the legend uses the same swatch. A
        // mid "Regular" band (3) is representative of the six-colour scale.
        SwatchRow(Palette.airQuality(3), "Calidad del aire (ICA)", "El Índice de Calidad del Aire de MITECO, del 1 (buena) al 6 (extremadamente desfavorable).")
    }
}

@Composable
private fun AvisoSection() {
    Section(header = "Avisos") {
        IconRow(Icons.Filled.Warning, "Aviso meteorológico", "AEMET tiene un aviso activo para la zona. El color indica el nivel: amarillo, naranja o rojo, de menor a mayor peligro.")
    }
}

@Composable
private fun AppSection() {
    Section(header = "En la app") {
        IconRow(Icons.Filled.Settings, "Menú", "El engranaje de arriba abre las secciones de la app.")
        IconRow(Icons.Filled.Place, "Ubicaciones", "Tus lugares guardados.")
        IconRow(Icons.Filled.Info, "Acerca de", "Versión, fuentes y créditos.")
        IconRow(Icons.Filled.Search, "Buscar", "Encuentra un municipio para añadirlo.")
        IconRow(Icons.Filled.Add, "Añadir", "Guarda una ubicación nueva.")
        IconRow(Icons.Filled.Check, "Elegida", "La ubicación que se está mostrando.")
        IconRow(Icons.Filled.KeyboardArrowDown, "Desplegar", "Desliza o toca para ver más detalle.")
        IconRow(Icons.Filled.Close, "Cerrar", "Cierra la ficha o la escala abierta.")
    }
}

@Composable
private fun ScalesSection() {
    Section(
        header = "Escalas de color",
        footer = "La temperatura usa la misma escala azul→rojo en toda la app: tarjetas, barras y widget.",
    ) {
        Body(
            "Muchas tarjetas se pueden tocar para abrir su escala de color, con tu valor actual señalado: " +
                "Viento (Beaufort), Calidad del aire (ICA) y UV.",
        )
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
