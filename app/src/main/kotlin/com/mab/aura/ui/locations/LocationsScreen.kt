package com.mab.aura.ui.locations

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mab.aura.core.model.Location

/**
 * "Ubicaciones" — the saved-places manager: use the device location, pick the active place, add from the
 * bundled city list, and delete. Android port of `LocationsView.swift`.
 *
 * Two deliberate divergences from the iOS list, both in the name of clarity and no extra dependencies:
 *   - Deleting is a trailing bin icon per row, not a swipe. It reads at a glance, needs no gesture to discover,
 *     and works with a plain click (SwiftUI's `.onDelete` leans on the swipe-to-delete idiom iOS users know).
 *   - Reordering (`.onMove`/`EditButton`) is left out for now. Compose has no built-in drag-reorder, and the
 *     list's job here is answered by *selecting* the active place directly rather than by its order. It returns
 *     with the full municipality search, if it earns its keep.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    onBack: () -> Unit,
    onAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LocationsViewModel = viewModel()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val activeINE by viewModel.activeINE.collectAsStateWithLifecycle()
    val resolving by viewModel.resolving.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // "Usar mi ubicación" needs the coarse grant. Ask for it here on demand (the screen owns the request, the
    // ViewModel only reads the grant); either outcome then runs useMyLocation, which resolves on a grant or
    // sets the "permission denied" notice otherwise.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.useMyLocation() }

    fun onUseMyLocation() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.useMyLocation() else locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ubicaciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onAddLocation) {
                        Icon(Icons.Filled.Add, contentDescription = "Añadir ubicación")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Add from the device's current position.
            item {
                UseMyLocationRow(resolving = resolving, onClick = ::onUseMyLocation)
                if (notice != null) {
                    Text(
                        text = notice!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                HorizontalDivider()
            }

            // The saved favourites, or a hint when there are none yet.
            if (favourites.isEmpty()) {
                item {
                    Text(
                        text = "No has guardado ninguna ubicación. Usa el botón + para añadir una ciudad, o tu ubicación actual.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(favourites, key = { it.ine }) { location ->
                    FavouriteRow(
                        location = location,
                        isActive = location.ine == activeINE,
                        onSelect = { viewModel.selectActive(location.ine) },
                        onRemove = { viewModel.remove(location) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The "Usar mi ubicación" action, with a trailing spinner while a fix is resolving. */
@Composable
private fun UseMyLocationRow(resolving: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !resolving, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Usar mi ubicación", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (resolving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

/** One saved place: tap the row to make it active (a check marks the current one), the bin to remove it. */
@Composable
private fun FavouriteRow(
    location: Location,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(location.nombre, style = MaterialTheme.typography.bodyLarge)
            Text(
                location.provincia,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The active-place tick sits in a fixed-width slot so the names below it stay left-aligned whether or
        // not a row is the active one.
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (isActive) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Ubicación activa",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Eliminar ${location.nombre}",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
