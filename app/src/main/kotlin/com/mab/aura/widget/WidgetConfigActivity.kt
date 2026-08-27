package com.mab.aura.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.mab.aura.R
import com.mab.aura.core.model.Location
import com.mab.aura.store.Settings
import com.mab.aura.ui.theme.AuraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The per-widget location picker: Android's answer to the iOS `SelectLocationIntent`. It lets one tile pin its
 * own place while another tile follows the app, so a Madrid tile and a Barcelona tile can sit side by side.
 *
 * It is declared as the widget's `android:configure` activity. Two things follow from that contract:
 *   - It is launched with the target tile's `EXTRA_APPWIDGET_ID`. We must echo that id back in the result Intent.
 *   - The result defaults to `RESULT_CANCELED`, set before anything else. At first-time placement, cancelling
 *     (Back) tells the launcher to drop the half-placed tile; a confirmed pick swaps it to `RESULT_OK`. On a
 *     later *reconfigure* (the tile already exists), cancelling simply leaves the tile as it was.
 *
 * The pick is written only to *this tile's* Glance state (`PreferencesGlanceStateDefinition`, keyed per
 * `GlanceId`), never to the app's `Settings` — so pinning a widget can't move the app's active location.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Default to cancelled: a Back out of first-time configuration then removes the tile.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        // Nothing to configure without a valid target (shouldn't happen via the normal launcher flow).
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            AuraTheme {
                WidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    onSelect = { ine -> confirmSelection(appWidgetId, ine) },
                    onBack = { finish() },
                )
            }
        }
    }

    /** Write the chosen INE (null = follow the app) into this tile's state, re-render it, and report success. */
    private fun confirmSelection(appWidgetId: Int, ine: String?) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(applicationContext, glanceId) { prefs ->
                if (ine == null) prefs.remove(PINNED_INE_KEY) else prefs[PINNED_INE_KEY] = ine
            }
            // Re-render every Aura tile so this one picks up its new pin (update() targets a single GlanceId, but
            // updateAll is simpler here and the others just re-read their unchanged state).
            AuraGlanceWidget().updateAll(applicationContext)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

/** What the picker shows once loaded: the tile's current pin (null = following the app) and the saved places. */
private data class ConfigData(val pinnedINE: String?, val favourites: List<Location>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    appWidgetId: Int,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // Read this tile's current pin and the app's favourites once; null while still loading (show a spinner).
    val data by produceState<ConfigData?>(initialValue = null, appWidgetId) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val pin = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[PINNED_INE_KEY]
        value = ConfigData(pin, Settings(context).favourites.first())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        val loaded = data
        if (loaded == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            // The default: follow whatever the app is showing. Clears any pin.
            item {
                OptionRow(
                    title = stringResource(R.string.widget_config_app_location_title),
                    subtitle = stringResource(R.string.widget_config_app_location_subtitle),
                    selected = loaded.pinnedINE == null,
                    onClick = { onSelect(null) },
                )
                HorizontalDivider()
            }

            if (loaded.favourites.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.widget_config_empty_favourites),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(loaded.favourites, key = { it.ine }) { location ->
                    OptionRow(
                        title = location.nombre,
                        subtitle = location.provincia,
                        selected = loaded.pinnedINE == location.ine,
                        onClick = { onSelect(location.ine) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** One pickable row: a title over a subtitle, with a trailing check on the current selection. */
@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Fixed-width slot so titles stay aligned whether or not a row is the selected one.
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.widget_config_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
