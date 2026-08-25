package com.mab.aura.ui.hoy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mab.aura.core.geo.SpainCities
import com.mab.aura.core.model.Location
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.time.AuraTime
import com.mab.aura.data.WeatherRepository
import com.mab.aura.location.LocationProvider
import com.mab.aura.location.LocationResult
import com.mab.aura.store.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The state behind the "Hoy" screen: it resolves which location to show, renders the cached snapshot
 * immediately, then refreshes from AEMET through the [WeatherRepository]. Android port of the "Hoy" view's
 * model role in `AEMETService` + the SwiftUI screen; exposed to Compose as a [StateFlow] of [HoyUiState].
 *
 * An [AndroidViewModel] because the repository and settings store need an application [android.content.Context];
 * it survives configuration changes (rotation), so a rotate doesn't re-fire the network refresh.
 */
class HoyViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = WeatherRepository(app)
    private val settings = Settings(app)
    private val locationProvider = LocationProvider(app)

    private val _state = MutableStateFlow<HoyUiState>(HoyUiState.Loading)
    val state: StateFlow<HoyUiState> = _state.asStateFlow()

    init {
        // Keep the shared clock formatter in step with the stored 24 h / 12 h preference. This is the one
        // place :app writes :core's AuraTime flag (see AuraTime's Android note); the cards read it synchronously.
        viewModelScope.launch {
            settings.use24h.collect { AuraTime.use24h = it }
        }
        // Reload whenever the active place changes, so picking a favourite in "Ubicaciones" (or the device
        // flow selecting a just-added city) makes "Hoy" follow it on return. drop(1) skips the current stored
        // value — the load() below already resolves with it — so only genuine later changes re-fetch.
        viewModelScope.launch {
            settings.activeINE.drop(1).distinctUntilChanged().collect { load() }
        }
        load()
    }

    /** (Re)load: resolve the location, show any cached snapshot, then refresh. Safe to call again on retry. */
    fun load() {
        viewModelScope.launch {
            _state.value = HoyUiState.Loading

            // Key entry lands with a later Settings screen; until then, no key means the screen can't fetch.
            if (!repository.hasApiKey()) {
                _state.value = HoyUiState.NeedsApiKey
                return@launch
            }

            val resolved = resolveLocation()
            val location = resolved.location

            // Show the last-known snapshot first, so an offline open still renders something immediately.
            repository.cachedSnapshot(location.ine)?.let {
                _state.value = HoyUiState.Content(it, locationFallback = resolved.fallback)
            }

            val error = repository.refresh(listOf(location))
            val fresh = repository.cachedSnapshot(location.ine)
            _state.value = when {
                // Data present: show it, with the error (if any) as a non-blocking notice over the top, and the
                // reason we fell back to a default location (if we did) for the screen to surface separately.
                fresh != null -> HoyUiState.Content(fresh, notice = error, locationFallback = resolved.fallback)
                // Nothing cached and the refresh failed: a full error state with a retry.
                else -> HoyUiState.Error(error ?: "No se pudo obtener la información.")
            }
        }
    }

    /**
     * The location to show, in order of preference: the active favourite, else the first favourite, else the
     * seed city nearest the device's current position, else a seeded Madrid as a last resort. A saved favourite
     * always wins over the device fix, matching iOS — GPS only seeds the picker. When we fall all the way back
     * to the default *because* the device fix wasn't usable, the reason rides along in [Resolved.fallback] so
     * the screen can say why (permission denied, services off, no fix); a favourite or a good fix carries none.
     *
     * The favourites/add-location UI is still a later step; this keeps the first screen live. The screen owns
     * the permission *request*; this only reads whatever grant is in place (port of `LocationManager.resolveNearestCity`).
     */
    private suspend fun resolveLocation(): Resolved {
        val favourites = settings.favourites.first()
        val activeIne = settings.activeINE.first()
        val favourite = favourites.firstOrNull { it.ine == activeIne } ?: favourites.firstOrNull()
        if (favourite != null) return Resolved(favourite, fallback = null)

        // No saved place: resolve the device's nearest municipality, or fall back to Madrid and record why.
        return when (val result = locationProvider.current()) {
            is LocationResult.Available -> Resolved(
                SpainCities.nearest(result.coordinate.latitude, result.coordinate.longitude),
                fallback = null,
            )
            LocationResult.PermissionDenied -> Resolved(DEFAULT_MADRID, LocationFallback.PermissionDenied)
            LocationResult.ServicesOff -> Resolved(DEFAULT_MADRID, LocationFallback.ServicesOff)
            LocationResult.NoFix -> Resolved(DEFAULT_MADRID, LocationFallback.NoFix)
        }
    }

    /** A resolved location together with the reason we fell back to a default, if we did. */
    private data class Resolved(val location: Location, val fallback: LocationFallback?)

    private companion object {
        val DEFAULT_MADRID = Location(
            ine = "28079",
            nombre = "Madrid",
            provincia = "Madrid",
            latitude = 40.4168,
            longitude = -3.7038,
        )
    }
}

/** What the "Hoy" screen is showing right now. */
sealed interface HoyUiState {
    /** First load, before any snapshot is available. */
    data object Loading : HoyUiState

    /** No AEMET key stored yet (key entry arrives with the Settings screen), so nothing can be fetched. */
    data object NeedsApiKey : HoyUiState

    /**
     * Weather to show. [notice] is a non-blocking banner (e.g. "offline, showing last data"), or null.
     * [locationFallback] is set only when we couldn't use the device position and fell back to a default place,
     * so the screen can explain why; the screen maps it to text and decides when to show it (see [LocationFallback]).
     */
    data class Content(
        val snapshot: WeatherSnapshot,
        val notice: String? = null,
        val locationFallback: LocationFallback? = null,
    ) : HoyUiState

    /** No data at all and the refresh failed; the screen offers a retry. */
    data class Error(val message: String) : HoyUiState
}

/**
 * Why "Hoy" is showing a default location (Madrid) instead of the device's own position. Mirrors the three
 * no-fix [LocationResult] states so the screen can show a distinct message for each, per `specs/location.md`.
 * The screen decides *when* to surface each one — notably it doesn't mention a missing permission until it has
 * actually prompted for it.
 */
enum class LocationFallback { PermissionDenied, ServicesOff, NoFix }
