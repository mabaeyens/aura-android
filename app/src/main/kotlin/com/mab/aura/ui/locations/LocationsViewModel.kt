package com.mab.aura.ui.locations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mab.aura.core.geo.SpainCities
import com.mab.aura.core.model.Location
import com.mab.aura.location.LocationProvider
import com.mab.aura.location.LocationResult
import com.mab.aura.store.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The state behind the "Ubicaciones" screen: the saved favourites, which one is active, and the actions that
 * change them (select, add, remove, add-from-device-location). Android port of `LocationStore.swift` — but the
 * persistence it owned on iOS already lives in the [Settings] DataStore here, so this is a thin ViewModel over
 * that store plus [LocationProvider], not a second source of truth.
 *
 * An [AndroidViewModel] because [Settings] and [LocationProvider] need an application context. All mutations are
 * read-modify-write over the current [favourites] value: fine for a single-user, tap-driven screen, and it keeps
 * [Settings] a plain key-value store rather than growing list-editing methods of its own.
 */
class LocationsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val locationProvider = LocationProvider(app)

    /** The saved places, in order, kept live from the store so the list updates as soon as a mutation lands. */
    val favourites: StateFlow<List<Location>> =
        settings.favourites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The active INE (the place "Hoy" shows), or null when nothing is selected yet. */
    val activeINE: StateFlow<String?> =
        settings.activeINE.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _resolving = MutableStateFlow(false)

    /** True while a device-location fix is being resolved (drives the spinner on "Usar mi ubicación"). */
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /** A transient Spanish message about the last "Usar mi ubicación" attempt, or null. Shown under the button. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Make [ine] the active place. A no-op reselect is harmless; the store deduplicates the write. */
    fun selectActive(ine: String) {
        viewModelScope.launch { settings.setActiveINE(ine) }
    }

    /**
     * Add [location] to the favourites and select it. If it is already saved, just select it (no duplicate),
     * matching `LocationStore.add`. Selecting the freshly added place is what makes "Hoy" jump to it on return.
     */
    fun add(location: Location) {
        viewModelScope.launch {
            val current = favourites.value
            if (current.none { it.ine == location.ine }) {
                settings.setFavourites(current + location)
            }
            settings.setActiveINE(location.ine)
        }
    }

    /**
     * Remove [location]. If it was the active place, fall the selection back to the first remaining favourite
     * (or null when none are left, which returns "Hoy" to the device/Madrid default). Port of `LocationStore.remove`.
     */
    fun remove(location: Location) {
        viewModelScope.launch {
            val next = favourites.value.filterNot { it.ine == location.ine }
            settings.setFavourites(next)
            if (activeINE.value == location.ine) {
                settings.setActiveINE(next.firstOrNull()?.ine)
            }
        }
    }

    /**
     * Resolve the device's current position to the nearest bundled municipality and add it. The screen owns the
     * permission *request*; this reads whatever grant is in place and, on any no-fix outcome, sets a [notice]
     * explaining why instead of adding anything. Port of `LocationsView`'s "Usar mi ubicación" action over
     * `LocationManager.resolveNearestCity`.
     */
    fun useMyLocation() {
        viewModelScope.launch {
            _resolving.value = true
            _notice.value = null
            when (val result = locationProvider.current()) {
                is LocationResult.Available ->
                    add(SpainCities.nearest(result.coordinate.latitude, result.coordinate.longitude))
                LocationResult.PermissionDenied ->
                    _notice.value = "Permiso de ubicación denegado. Actívalo en los Ajustes del sistema."
                LocationResult.ServicesOff ->
                    _notice.value = "La ubicación está desactivada. Actívala para usar tu posición."
                LocationResult.NoFix ->
                    _notice.value = "No se pudo determinar tu ubicación. Inténtalo de nuevo."
            }
            _resolving.value = false
        }
    }
}
