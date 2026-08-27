package com.mab.aura.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.aura.R
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
                title = { Text(stringResource(R.string.about_title)) },
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

            Text(stringResource(R.string.about_app_name), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(28.dp))

            // The "what Aura is" blurb, ported verbatim from AboutView. Written as me, first person where it
            // speaks ("Aura es..."), centred like the iOS screen.
            CenteredBody(stringResource(R.string.about_blurb))

            Spacer(Modifier.height(28.dp))

            SmallHeader(stringResource(R.string.about_dedication_header))
            // A personal dedication, in my own words. Not an attribution: kept apart from the credits below.
            CenteredBody(stringResource(R.string.about_dedication_father), modifier = Modifier.padding(bottom = 8.dp))
            CenteredBody(stringResource(R.string.about_dedication_mother), modifier = Modifier.padding(bottom = 8.dp))

            Spacer(Modifier.height(28.dp))

            SmallHeader(stringResource(R.string.about_credits_header))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 360.dp),
            ) {
                for (c in credits) {
                    CreditRow(name = c.name, provides = stringResource(c.provides), onClick = { open(c.url) })
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.about_github),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { open("https://github.com/mabaeyens/aura-android") },
            )
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class Credit(val name: String, @StringRes val provides: Int, val url: String)

/** The source list, same order and wording as the iOS AboutView Créditos block. The `provides` note is a
 *  string resource so it can be localised; the source names and URLs are not translated. */
private val credits = listOf(
    Credit("AEMET", R.string.about_credit_aemet, "https://opendata.aemet.es"),
    Credit(
        "MITECO",
        R.string.about_credit_miteco,
        "https://www.miteco.gob.es/es/calidad-y-evaluacion-ambiental/temas/atmosfera-y-calidad-del-aire/visualizacion-datos-calidad-del-aire/ica.html",
    ),
    Credit("Copernicus (CAMS)", R.string.about_credit_copernicus, "https://atmosphere.copernicus.eu"),
    Credit("RTVE", R.string.about_credit_rtve, "https://www.rtve.es"),
    Credit("Meteored", R.string.about_credit_meteored, "https://www.tiempo.com"),
    Credit("AEMET Blog", R.string.about_credit_aemetblog, "https://aemetblog.es"),
    Credit("Meteocons", R.string.about_credit_meteocons, "https://github.com/basmilius/weather-icons"),
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
