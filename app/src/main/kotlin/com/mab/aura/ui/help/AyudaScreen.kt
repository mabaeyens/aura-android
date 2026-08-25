package com.mab.aura.ui.help

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mab.aura.ui.ConditionGlyph

/**
 * "Ayuda" — the reference screen ported from `HelpView.swift`. It does two jobs: explain how to get a free
 * AEMET key, and give a legend for the app's icons and cards. Reached from the hero gear menu, next to
 * Acerca de (see [com.mab.aura.ui.hoy.HoyScreen]).
 *
 * What differs from the iOS screen, and why: the metric-card rows on iOS use SF Symbols (humidity, umbrella,
 * sunrise, the UV badges…). This project ships only `material-icons-core` on purpose, which has none of those,
 * and I don't want to pull in the large extended icon set just for a legend. So the sky-condition legend uses
 * the app's real [ConditionGlyph] (identical to the cards, can't drift), the navigation legend uses the core
 * icons the app already shows, and the per-metric cards are described in words rather than with a stand-in
 * icon. The colour scales aren't repeated here: each card opens its own on a tap, and this points there.
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
            CardsSection()
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

/**
 * The metric cards, described in words. On iOS each row has its own SF Symbol; here they are text because the
 * core icon set has no match (see the class note). Grouped by card so the meaning still reads as a legend.
 */
@Composable
private fun CardsSection() {
    Section(
        header = "Las tarjetas",
        footer = "La gota con ondas (humedad) y el paraguas o la gota lisa (lluvia) son cosas distintas a propósito.",
    ) {
        TextRow("Temperatura máxima y mínima", "La más alta y la más baja previstas para el día. Los grados se colorean en una escala continua de azul (frío) a rojo (calor), la misma en las tarjetas, las barras de rango y el widget.")
        TextRow("Probabilidad de lluvia", "El porcentaje de que llueva. Es la lluvia.")
        TextRow("Humedad relativa", "El agua que hay en el aire, en %. No es la lluvia.")
        TextRow("Viento", "La velocidad en km/h, la unidad que da AEMET; la flecha señala la dirección y su color, la intensidad. Toca la tarjeta para ver la escala Beaufort completa.")
        TextRow("Amanecer y atardecer", "La salida (orto) y la puesta (ocaso) del sol.")
        TextRow("Índice UV", "El máximo previsto del día con el cielo despejado. Toca la tarjeta para ver cada nivel, de bajo a extremadamente alto; el cielo nublado puede bajar el UV de ahora por debajo de ese máximo.")
        TextRow("Calidad del aire (ICA)", "El Índice de Calidad del Aire de MITECO. Toca la tarjeta para ver la escala completa y cada contaminante por separado.")
        TextRow("Aviso meteorológico", "AEMET tiene un aviso activo para la zona. El color indica el nivel: amarillo, naranja o rojo, de menor a mayor peligro.")
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

/** A legend row whose glyph is a weather condition, drawn by the same [ConditionGlyph] the cards use so the
 *  legend can't drift. Each rides a small day/night sky tile, since Aura's clouds are near-white and would
 *  vanish on the plain surface. */
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
            ConditionGlyph(sky = code, isNight = night, modifier = Modifier.size(22.dp))
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

/** A pure-text legend row: a title over a meaning, for the metric cards that have no core icon. */
@Composable
private fun TextRow(title: String, meaning: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            text = meaning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
