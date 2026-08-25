package com.mab.aura.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mab.aura.core.hero.HeroBackground
import com.mab.aura.store.SecretStore
import com.mab.aura.store.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The state behind "Ajustes": the AEMET key's presence and the 24 h / 12 h clock preference. Android port of
 * the model role in `SettingsView.swift` + `LocationStore`'s key-state tracking.
 *
 * The key itself never lives here — it is written straight through [SecretStore] (encrypted) and only its
 * *presence* is exposed, so the plaintext exists no longer than the text field in the screen. The clock
 * preference reads and writes the shared [Settings] DataStore, the same store [com.mab.aura.ui.hoy.HoyViewModel]
 * watches to keep [com.mab.aura.core.time.AuraTime] in step.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val secretStore = SecretStore(app)

    private val _apiKeyPresent = MutableStateFlow(!secretStore.apiKey().isNullOrEmpty())
    /** Whether a key is currently stored — drives the footer status and the "Borrar" button's visibility. */
    val apiKeyPresent: StateFlow<Boolean> = _apiKeyPresent.asStateFlow()

    private val _justSaved = MutableStateFlow(false)
    /** True once a key has been saved this visit, so the screen can show a one-off "Clave actualizada." line. */
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    /**
     * The stored clock preference (true = 24 h). Backed by the DataStore flow; `WhileSubscribed` lets the
     * upstream collector stop shortly after the screen leaves, and `true` matches the store's own default.
     */
    val use24h: StateFlow<Boolean> =
        settings.use24h.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The stored hero-art family (Paisaje / Ciudad), decoded from the setting string; landscape by default. */
    val heroFamily: StateFlow<HeroBackground.Family> = settings.heroFamily
        .map { HeroBackground.Family.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeroBackground.Family.LANDSCAPE)

    /** Store (or replace) the AEMET key. A blank string clears it (see [SecretStore.setApiKey]). */
    fun saveKey(key: String) {
        secretStore.setApiKey(key)
        _apiKeyPresent.value = !secretStore.apiKey().isNullOrEmpty()
        _justSaved.value = true
    }

    /** Remove the stored key. */
    fun clearKey() {
        secretStore.setApiKey("")
        _apiKeyPresent.value = !secretStore.apiKey().isNullOrEmpty()
        _justSaved.value = false
    }

    /** Persist the 24 h / 12 h choice; the DataStore write flows back to [use24h] and to AuraTime. */
    fun setUse24h(value: Boolean) {
        viewModelScope.launch { settings.setUse24h(value) }
    }

    /** Persist the hero-art family; the write flows back to [heroFamily] and to the "Hoy" sky on return. */
    fun setHeroFamily(family: HeroBackground.Family) {
        viewModelScope.launch { settings.setHeroFamily(family.name) }
    }
}
