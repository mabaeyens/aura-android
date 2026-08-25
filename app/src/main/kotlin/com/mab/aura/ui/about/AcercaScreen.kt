package com.mab.aura.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.aura.ui.ConditionGlyph

/**
 * "Acerca de" — the info screen ported from `AboutView.swift`. App icon, version, what Aura is, the personal
 * dedication, and the credits list. On iOS this hangs off Ajustes; here it is one of the entries in the hero
 * gear menu, alongside Ayuda (see [com.mab.aura.ui.hoy.HoyScreen]).
 *
 * Android note: the credit and repo links open the system browser through an `ACTION_VIEW` intent, which is
 * the platform's equivalent of SwiftUI's `Link`. There is no bundled rich-link control, so a tappable line of
 * text plus the intent is the standard, well-documented way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcercaScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
                else @Suppress("DEPRECATION") info.versionCode.toLong()
            "${info.versionName} ($code)"
        }.getOrDefault("—")
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Acerca de") },
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
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The app has no launcher icon yet, so — like the iOS AboutView's no-icon fallback — this shows a
            // branded rounded tile: the deep Aura blue with the app's own Meteocons sun on it. Swap in the real
            // icon here once one exists.
            Box(
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(red = 0.07f, green = 0.22f, blue = 0.37f)),
                contentAlignment = Alignment.Center,
            ) {
                ConditionGlyph(sky = "11", isNight = false, modifier = Modifier.size(56.dp))
            }

            Text("Aura", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Versión $version",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(28.dp))

            // The "what Aura is" blurb, ported verbatim from AboutView. Written as me, first person where it
            // speaks ("Aura es..."), centred like the iOS screen.
            CenteredBody(
                "Aura es una app del tiempo para Android. Toma tu ubicación más cercana y te muestra la " +
                    "previsión de AEMET a todo color: en la propia app y en el widget de la pantalla de inicio. " +
                    "Se actualiza sola a medida que cambian los datos.\n\n" +
                    "Del latín aura: brisa, aire en movimiento, y también el halo de luz que rodea algo.\n\n" +
                    "Los datos son de fuentes públicas oficiales. Todo ocurre en tu dispositivo: sin cuenta y " +
                    "sin servidores propios; solo se conecta a esas fuentes para traer los datos.",
            )

            Spacer(Modifier.height(28.dp))

            SmallHeader("Dedicatoria")
            // A personal dedication, in my own words. Not an attribution: kept apart from the credits below.
            for (line in dedicationLines) {
                CenteredBody(line, modifier = Modifier.padding(bottom = 8.dp))
            }

            Spacer(Modifier.height(28.dp))

            SmallHeader("Créditos")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 360.dp),
            ) {
                for (c in credits) {
                    CreditRow(name = c.name, provides = c.provides, onClick = { open(c.url) })
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Código en GitHub",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { open("https://github.com/mabaeyens/aura-android") },
            )
            Text(
                text = "Sin cuenta · sin servidores · solo fuentes públicas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The dedication lines, kept in one list so I can add names later without touching the layout. Verbatim from the iOS AboutView, my own words. */
private val dedicationLines = listOf(
    "A mi padre, que me enseñó a mirar al cielo y a las estrellas, y me introdujo en el fabuloso mundo de la tecnología que tanto disfruto hoy.",
    "A mi madre, que me descubrió otros mundos que no se podían ver con los ojos, a través de los libros y en los que encuentro gran dicha.",
)

private data class Credit(val name: String, val provides: String, val url: String)

/** The source list, same order and wording as the iOS AboutView Créditos block. */
private val credits = listOf(
    Credit("AEMET", "Previsión, avisos, radar y UV máximo (OpenData)", "https://opendata.aemet.es"),
    Credit(
        "MITECO",
        "Índice de calidad del aire (ICA · CC-BY 4.0)",
        "https://www.miteco.gob.es/es/calidad-y-evaluacion-ambiental/temas/atmosfera-y-calidad-del-aire/visualizacion-datos-calidad-del-aire/ica.html",
    ),
    Credit("Copernicus (CAMS)", "Índice UV por hora, vía Open-Meteo (CC-BY 4.0)", "https://atmosphere.copernicus.eu"),
    Credit("RTVE", "El Tiempo, el parte diario", "https://www.rtve.es"),
    Credit("Meteored", "Noticias y divulgación (tiempo.com)", "https://www.tiempo.com"),
    Credit("AEMET Blog", "Divulgación de sus meteorólogos", "https://aemetblog.es"),
    Credit("Meteocons", "Iconos del tiempo, de Bas Milius (licencia MIT)", "https://github.com/basmilius/weather-icons"),
)

/** A small, quiet section label, matching the footnote-weight headers on the iOS screen. */
@Composable
private fun SmallHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/** Centred secondary body text, capped to a comfortable measure like the iOS 360-point frame. */
@Composable
private fun CenteredBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
    )
}

/** One credit: the tappable source name over a one-line note of what it provides. */
@Composable
private fun CreditRow(name: String, provides: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = provides,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
