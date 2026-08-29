package com.mab.aura.ui.hoy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mab.aura.R
import com.mab.aura.core.geo.SpainCities
import com.mab.aura.core.hero.HeroBackground
import com.mab.aura.core.model.Location
import com.mab.aura.core.model.NewsItem
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.net.NewsService
import com.mab.aura.core.time.AuraTime
import com.mab.aura.data.NationalForecastRepository
import com.mab.aura.data.RadarRepository
import com.mab.aura.data.SurfaceAnalysisRepository
import com.mab.aura.data.WeatherRepository
import com.mab.aura.location.LocationProvider
import com.mab.aura.location.LocationResult
import com.mab.aura.store.Settings
import com.mab.aura.ui.cards.AuraRadarInfo
import com.mab.aura.ui.cards.AuraSurfaceInfo
import com.mab.aura.ui.cards.NationalForecastState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val radarRepository = RadarRepository(app)
    private val surfaceRepository = SurfaceAnalysisRepository(app)
    private val nationalRepository = NationalForecastRepository(app)
    private val newsService = NewsService()
    private val settings = Settings(app)
    private val locationProvider = LocationProvider(app)

    private val _state = MutableStateFlow<HoyUiState>(HoyUiState.Loading)
    val state: StateFlow<HoyUiState> = _state.asStateFlow()

    /** Whether a manual pull-to-refresh is in flight, so the screen can show the pull spinner without
     *  flipping the whole screen to the full loading state. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Wall-clock time (ms) of the last manual refresh we actually let through, so repeated pulls inside the
     *  window below become no-ops. 0 means "no manual refresh yet this session". */
    private var lastRefreshAtMillis = 0L

    /** Minimum gap between two manual refreshes that hit the network. A pull-to-refresh is a coarse gesture a
     *  user can fire many times in a row; AEMET is rate-limited, so we let one through then ignore the rest for
     *  a minute. Matches the 60 s window the client-side RequestPacer already uses. */
    private val refreshCooldownMillis = 60_000L

    /** Which hero-art family paints the sky, following the stored setting live so a change in Ajustes shows
     *  on return. Decoded from the `Settings` string; defaults to landscape, matching iOS. */
    val heroFamily: StateFlow<HeroBackground.Family> = settings.heroFamily
        .map { HeroBackground.Family.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeroBackground.Family.LANDSCAPE)

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
        // Tidy stale radar frames left in the cache from earlier sessions (>24 h), matching iOS's launch-time
        // prune. Cheap and off the main path — the live TTL is 10 min, so these are dead weight.
        radarRepository.pruneCache()
        // Same tidy for stale surface-analysis maps (>48 h; the live TTL is 12 h).
        surfaceRepository.pruneCache()
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
                else -> HoyUiState.Error(error ?: getApplication<Application>().getString(R.string.hoy_error_generic))
            }

            // Radar and news are fetched lazily, after the forecast is on screen and off other hosts (news is
            // public RSS; radar goes through AEMET but on its own 10-min cadence). Each fills its card in when it
            // arrives; a failure just leaves that card absent. They must never block or fail the forecast above.
            if (fresh != null) loadExtras(location)
        }
    }

    /**
     * Manual pull-to-refresh. Unlike [load] it forces the fetch past the 1-hour freshness gate and keeps the
     * current content on screen (with a small pull spinner via [isRefreshing]) instead of flashing the full
     * loading state. This is the escape hatch from a thin cache: without it a cold-start thin snapshot would
     * pin the screen to "--" until the gate expires. Needs a key and a resolvable location, like [load].
     */
    fun refresh() {
        viewModelScope.launch {
            if (!repository.hasApiKey()) {
                _state.value = HoyUiState.NeedsApiKey
                return@launch
            }
            // Time-gate the gesture: once a refresh goes through, ignore further pulls for a minute so a run of
            // consecutive swipes doesn't fire a run of API calls. A skipped pull just no-ops — we never flip
            // isRefreshing, so the pull spinner settles back on its own. The window starts when the fetch fires.
            val now = System.currentTimeMillis()
            if (now - lastRefreshAtMillis < refreshCooldownMillis) return@launch
            lastRefreshAtMillis = now

            _isRefreshing.value = true
            try {
                val resolved = resolveLocation()
                val location = resolved.location
                val error = repository.refresh(listOf(location), force = true)
                val fresh = repository.cachedSnapshot(location.ine)
                _state.value = when {
                    fresh != null -> HoyUiState.Content(fresh, notice = error, locationFallback = resolved.fallback)
                    else -> HoyUiState.Error(error ?: getApplication<Application>().getString(R.string.hoy_error_generic))
                }
                if (fresh != null) loadExtras(location)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Fetch the radar frame, the surface analysis map, the national text forecast and the news stream for
     * [location] concurrently, folding each into the current [HoyUiState.Content] as it lands. Guarded by INE:
     * if the user has since switched place (the state now shows a different snapshot), a late result is dropped
     * rather than painting the wrong location's card. The surface map and national forecast are
     * location-independent (one España-level product each), but they're guarded the same way so a late arrival
     * doesn't fight a place switch mid-fetch.
     */
    private fun loadExtras(location: Location) {
        viewModelScope.launch {
            val frame = radarRepository.frame(location)
            _state.update { s ->
                if (s is HoyUiState.Content && s.snapshot.ine == location.ine) s.copy(radar = frame) else s
            }
        }
        viewModelScope.launch {
            val map = surfaceRepository.map()
            _state.update { s ->
                if (s is HoyUiState.Content && s.snapshot.ine == location.ine) s.copy(surface = map) else s
            }
        }
        viewModelScope.launch {
            // Only today's national product fetches here (gated to ≤1/6 h in the repository); the sheet's other
            // three segments fetch lazily when opened, so they're wired as suspend loaders, not called now.
            val today = nationalRepository.today()
            val national = today?.let {
                NationalForecastState(
                    today = it,
                    loadManana = { nationalRepository.manana() },
                    loadPasadoManana = { nationalRepository.pasadoManana() },
                    loadMedioPlazo = { nationalRepository.medioPlazo() },
                )
            }
            _state.update { s ->
                if (s is HoyUiState.Content && s.snapshot.ine == location.ine) s.copy(national = national) else s
            }
        }
        viewModelScope.launch {
            val items = newsService.latest()
            _state.update { s ->
                if (s is HoyUiState.Content && s.snapshot.ine == location.ine) s.copy(news = items) else s
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
     * [radar], [surface], [national] and [news] arrive after the forecast (fetched separately, kept out of the
     * snapshot); they start null/empty and fill in when their fetch lands, so their cards appear a moment later.
     */
    data class Content(
        val snapshot: WeatherSnapshot,
        val notice: String? = null,
        val locationFallback: LocationFallback? = null,
        val radar: AuraRadarInfo? = null,
        val surface: AuraSurfaceInfo? = null,
        val national: NationalForecastState? = null,
        val news: List<NewsItem> = emptyList(),
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
