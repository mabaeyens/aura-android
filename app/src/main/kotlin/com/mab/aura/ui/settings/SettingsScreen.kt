package com.mab.aura.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mab.aura.R
import com.mab.aura.core.hero.HeroBackground

/**
 * "Ajustes" — enter the AEMET key, choose the clock format, and pick the sky-art family. Android port of
 * `SettingsView.swift`, pared to what the port wires today: key entry, the 24 h / 12 h toggle, and the
 * Paisaje / Ciudad hero picker. Notificaciones (no notification path ported) is deliberately left out.
 *
 * Unlike the "Hoy" screen, this is an ordinary Material 3 [Scaffold] with a top bar and the theme surface
 * behind it — a settings list wants the standard, legible chrome, not the full-bleed sky. [onBack] is the
 * caller's back action (see [com.mab.aura.MainActivity]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel()
    val apiKeyPresent by viewModel.apiKeyPresent.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()
    val use24h by viewModel.use24h.collectAsStateWithLifecycle()
    val heroFamily by viewModel.heroFamily.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ApiKeySection(
                apiKeyPresent = apiKeyPresent,
                justSaved = justSaved,
                onSave = viewModel::saveKey,
                onClear = viewModel::clearKey,
            )
            HorizontalDivider()
            ClockSection(use24h = use24h, onChange = viewModel::setUse24h)
            HorizontalDivider()
            HeroFamilySection(family = heroFamily, onChange = viewModel::setHeroFamily)
            HorizontalDivider()
            AboutSection()
        }
    }
}

/** AEMET key entry: a masked field, a save (disabled while blank), an optional clear, and a status line. */
@Composable
private fun ApiKeySection(
    apiKeyPresent: Boolean,
    justSaved: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    // The plaintext key lives only here, in local screen state, and is cleared the moment it is saved.
    var keyInput by rememberSaveable { mutableStateOf("") }

    SectionHeader(stringResource(R.string.settings_api_key_header))
    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it },
        label = { Text(stringResource(R.string.settings_aemet_key_label)) },
        singleLine = true,
        // Mask the token and switch off autocapitalisation/autocorrect — it is an opaque credential, not prose.
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = {
                onSave(keyInput)
                keyInput = ""
            },
            enabled = keyInput.isNotBlank(),
        ) {
            Text(stringResource(R.string.settings_save_key))
        }
        if (apiKeyPresent) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.settings_clear_key), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    Text(
        text = if (apiKeyPresent) stringResource(R.string.settings_key_stored)
        else stringResource(R.string.settings_key_absent),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (justSaved) {
        Text(
            text = stringResource(R.string.settings_key_updated),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        text = stringResource(R.string.settings_key_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The 24 h / 12 h clock choice as a two-segment control, applied everywhere through AuraTime. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockSection(use24h: Boolean, onChange: (Boolean) -> Unit) {
    SectionHeader(stringResource(R.string.settings_clock_header))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = use24h,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_clock_24h)) }
        SegmentedButton(
            selected = !use24h,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_clock_12h)) }
    }
    Text(
        text = stringResource(R.string.settings_clock_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The hero-art family: Paisaje (landscape) or Ciudad (cityscape), the two illustrated sky sets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroFamilySection(family: HeroBackground.Family, onChange: (HeroBackground.Family) -> Unit) {
    SectionHeader(stringResource(R.string.settings_hero_header))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = family == HeroBackground.Family.LANDSCAPE,
            onClick = { onChange(HeroBackground.Family.LANDSCAPE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_hero_landscape)) }
        SegmentedButton(
            selected = family == HeroBackground.Family.CITYSCAPE,
            onClick = { onChange(HeroBackground.Family.CITYSCAPE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_hero_cityscape)) }
    }
    Text(
        text = stringResource(R.string.settings_hero_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Attribution and the build's version string. */
@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
                else @Suppress("DEPRECATION") info.versionCode.toLong()
            "${info.versionName} ($code)"
        }.getOrDefault("?")
    }

    SectionHeader(stringResource(R.string.settings_about_header))
    Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyMedium)
    Text(
        text = stringResource(R.string.settings_about_data),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.settings_about_icons),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
