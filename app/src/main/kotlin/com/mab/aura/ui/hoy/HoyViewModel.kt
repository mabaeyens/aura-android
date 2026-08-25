package com.mab.aura.ui.hoy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mab.aura.core.model.Location
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.time.AuraTime
import com.mab.aura.data.WeatherRepository
import com.mab.aura.store.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _state = MutableStateFlow<HoyUiState>(HoyUiState.Loading)
    val state: StateFlow<HoyUiState> = _state.asStateFlow()

    init {
        // Keep the shared clock formatter in step with the stored 24 h / 12 h preference. This is the one
        // place :app writes :core's AuraTime flag (see AuraTime's Android note); the cards read it synchronously.
        viewModelScope.launch {
            settings.use24h.collect { AuraTime.use24h = it }
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

            val location = resolveLocation()

            // Show the last-known snapshot first, so an offline open still renders something immediately.
            repository.cachedSnapshot(location.ine)?.let { _state.value = HoyUiState.Content(it) }

            val error = repository.refresh(listOf(location))
            val fresh = repository.cachedSnapshot(location.ine)
            _state.value = when {
                // Data present: show it, with the error (if any) as a non-blocking notice over the top.
                fresh != null -> HoyUiState.Content(fresh, notice = error)
                // Nothing cached and the refresh failed: a full error state with a retry.
                else -> HoyUiState.Error(error ?: "No se pudo obtener la información.")
            }
        }
    }

    /**
     * The location to show: the active favourite, else the first favourite, else a seeded Madrid so the very
     * first launch (no favourites yet) still has real weather to render. A favourites/add-location UI and
     * device-location resolution are later steps; this keeps the first screen live in the meantime.
     */
    private suspend fun resolveLocation(): Location {
        val favourites = settings.favourites.first()
        val activeIne = settings.activeINE.first()
        return favourites.firstOrNull { it.ine == activeIne }
            ?: favourites.firstOrNull()
            ?: DEFAULT_MADRID
    }

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

    /** Weather to show. [notice] is a non-blocking banner (e.g. "offline, showing last data"), or null. */
    data class Content(val snapshot: WeatherSnapshot, val notice: String? = null) : HoyUiState

    /** No data at all and the refresh failed; the screen offers a retry. */
    data class Error(val message: String) : HoyUiState
}
