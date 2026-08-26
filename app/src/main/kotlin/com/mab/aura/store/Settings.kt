package com.mab.aura.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mab.aura.core.model.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant

// One DataStore for the whole app, created once per process by this delegate (the recommended pattern). The
// Glance widget, when it lands, shares the app process and reads this same instance — no App Group needed,
// which is why the Swift `SharedCache` defaults and `SharedLocations` both fold into here (plan, Layer D).
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_settings")

/**
 * The app's small key-value state: which location is active, the saved favourites, and the 24 h / 12 h clock
 * preference. Android port of the defaults half of `SharedCache.swift` plus `SharedLocations.swift` and the
 * `AuraTime.use24h` flag, all folded onto one Jetpack DataStore (`specs/android-port.md`, Layer D).
 *
 * Everything is exposed as a [Flow] (DataStore is asynchronous by design) with a suspending setter. The
 * `watchSelectedINE` key is dropped — there is no watch surface on Android.
 */
class Settings(context: Context) {

    private val store = context.applicationContext.dataStore

    /**
     * The INE the app currently considers active (the location on screen). An unconfigured widget follows
     * this rather than an arbitrary favourite. Null when nothing has been selected yet.
     */
    val activeINE: Flow<String?> = store.data.map { it[ACTIVE_INE] }

    suspend fun setActiveINE(ine: String?) {
        store.edit { prefs ->
            if (ine == null) prefs.remove(ACTIVE_INE) else prefs[ACTIVE_INE] = ine
        }
    }

    /**
     * The user's saved locations, in their order. This is the *menu* of places (what the favourites list and
     * the widget picker offer), distinct from the weather cache in [SnapshotCache]. Stored as a JSON array of
     * [Location]; a malformed value decodes to an empty list, matching the Swift's `?? []`.
     */
    val favourites: Flow<List<Location>> = store.data.map { prefs ->
        prefs[FAVOURITES]?.let { decodeFavourites(it) } ?: emptyList()
    }

    suspend fun setFavourites(locations: List<Location>) {
        val encoded = json.encodeToString(locations)
        store.edit { it[FAVOURITES] = encoded }
    }

    /**
     * The clock preference: true (default) is 24-hour, false is 12-hour. Spain runs 24 h, so the default
     * before anything is written matches [com.mab.aura.core.time.AuraTime.use24h].
     */
    val use24h: Flow<Boolean> = store.data.map { it[USE_24H] ?: true }

    suspend fun setUse24h(value: Boolean) {
        store.edit { it[USE_24H] = value }
    }

    /**
     * Which hero-art family paints the sky: "LANDSCAPE" (default) or "CITYSCAPE", the two 48-image grids.
     * Stored as the `HeroBackground.Family` enum name; an unknown value decodes back to LANDSCAPE. Mirrors
     * the iOS `@AppStorage("heroFamily")` default (`.landscape`).
     */
    val heroFamily: Flow<String> = store.data.map { it[HERO_FAMILY] ?: "LANDSCAPE" }

    suspend fun setHeroFamily(value: String) {
        store.edit { it[HERO_FAMILY] = value }
    }

    /**
     * The measurement time (`fint`) of the freshest record from the last successful national observation
     * fetch (`/observacion/convencional/todas`). That product updates once per hour, so the refresh path
     * uses this to hold the last-known feed until the next hourly reading is due instead of re-downloading it
     * every cycle (see [com.mab.aura.data.observationDue]). Null until the first successful fetch (then it
     * fetches anyway). This is the Android home for the Swift `SharedCache.lastObservationFint` App Group
     * value, stored as epoch millis. A widget update never writes it — only a real refresh does.
     */
    val lastObservationFint: Flow<Instant?> = store.data.map { prefs ->
        prefs[LAST_OBSERVATION_FINT]?.let { Instant.ofEpochMilli(it) }
    }

    suspend fun setLastObservationFint(value: Instant?) {
        store.edit { prefs ->
            if (value == null) prefs.remove(LAST_OBSERVATION_FINT)
            else prefs[LAST_OBSERVATION_FINT] = value.toEpochMilli()
        }
    }

    private fun decodeFavourites(raw: String): List<Location> =
        runCatching { json.decodeFromString<List<Location>>(raw) }.getOrDefault(emptyList())

    private companion object {
        val ACTIVE_INE = stringPreferencesKey("active_ine")
        val FAVOURITES = stringPreferencesKey("favourites_json")
        val USE_24H = booleanPreferencesKey("use_24h")
        val HERO_FAMILY = stringPreferencesKey("hero_family")
        val LAST_OBSERVATION_FINT = longPreferencesKey("last_observation_fint")

        val json = Json { ignoreUnknownKeys = true }
    }
}
