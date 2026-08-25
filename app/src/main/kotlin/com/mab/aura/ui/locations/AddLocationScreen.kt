package com.mab.aura.ui.locations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mab.aura.core.model.Location
import com.mab.aura.data.Municipios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * "Añadir ubicación" — a searchable picker over the full Spanish municipality table ([Municipios.all], the
 * ~8,100-entry INE list bundled as an asset). Android port of `AddLocationView.swift`. If the bundled table
 * hasn't been generated yet, [Municipios] falls back to the 54-city seed, so this screen always has data.
 *
 * The search is accent- *and* case-insensitive: each entry is paired once with a folded key (diacritics
 * stripped, lowercased) so typing "malaga" finds "Málaga" and "avila" finds "Ávila", which matters a lot more
 * over 8,100 municipalities than it did over 54 capitals. The table loads off the main thread (it's ~1 MB to
 * parse), showing a spinner until it arrives.
 *
 * It shares the activity-scoped [LocationsViewModel] with [LocationsScreen] (there is no NavHost, so `viewModel()`
 * resolves the same instance), so selecting a city adds it to the same favourites list and returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LocationsViewModel = viewModel()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    // The full table loads off the main thread on first composition; `indexed` is empty until it arrives.
    // Each municipality is paired once with a folded search key (accents stripped, lowercased) so filtering on
    // every keystroke is a plain substring test over precomputed strings rather than re-normalising 8,100 rows.
    val indexed by produceState(initialValue = emptyList<Pair<Location, String>>()) {
        value = withContext(Dispatchers.IO) {
            Municipios.all(context)
                .sortedBy { it.nombre.lowercase() }
                .map { it to fold("${it.nombre} ${it.provincia}") }
        }
    }
    val loading = indexed.isEmpty()
    val results = remember(query, indexed) {
        val q = fold(query.trim())
        if (q.isEmpty()) indexed.map { it.first }
        else indexed.filter { it.second.contains(q) }.map { it.first }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Añadir ubicación") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar municipio") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.ine }) { location ->
                        CityRow(location = location, onClick = {
                            viewModel.add(location)
                            onBack()
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** A regex matching the Unicode combining marks that NFD decomposition splits accents into, compiled once. */
private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * Fold a string for accent- and case-insensitive search: decompose to NFD so each accented letter becomes a
 * base letter plus a combining mark, drop the marks, then lowercase. "Málaga" and "malaga" both fold to
 * "malaga". This is the Android/JVM equivalent of iOS's `String.folding(options: .diacriticInsensitive)`.
 */
private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()

/** One city in the picker: its name over the province, tap to add and return. */
@Composable
private fun CityRow(location: Location, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(location.nombre, style = MaterialTheme.typography.bodyLarge)
        Text(
            location.provincia,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
