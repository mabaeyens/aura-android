package com.mab.aura.ui.locations

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mab.aura.core.geo.SpainCities
import com.mab.aura.core.model.Location

/**
 * "Añadir ubicación" — a searchable picker over the bundled city list ([SpainCities.seed]). Android port of
 * `AddLocationView.swift`; Phase 1 ships the provincial capitals and a few major cities, so this searches that
 * bundled table, not a live municipality service (that lands with the full INE table later).
 *
 * It shares the activity-scoped [LocationsViewModel] with [LocationsScreen] (there is no NavHost, so `viewModel()`
 * resolves the same instance), so selecting a city adds it to the same favourites list and returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LocationsViewModel = viewModel()
    var query by remember { mutableStateOf("") }

    // The seed sorted by name once; the query filters that list on name or province, case-insensitively.
    val sorted = remember { SpainCities.seed.sortedBy { it.nombre.lowercase() } }
    val results = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) sorted
        else sorted.filter { it.nombre.contains(q, ignoreCase = true) || it.provincia.contains(q, ignoreCase = true) }
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
