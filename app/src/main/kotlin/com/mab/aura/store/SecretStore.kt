package com.mab.aura.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The one secret Aura stores: the AEMET API key. Android port of `AuraKeychain` (`Keychain.swift`).
 *
 * iOS keeps this in the Keychain; the Android equivalent is the hardware-backed Keystore. Rather than drive
 * the Keystore directly, this uses Jetpack Security's [EncryptedSharedPreferences], which stores the value in
 * an ordinary preferences file but encrypts both keys and values with a Keystore-held master key — the
 * ergonomic wrapper the plan chose (see `specs/android-port.md`, Layer D).
 *
 * A note on the dependency: Google has deprecated `androidx.security:security-crypto`, and 1.1.0-alpha06 is
 * its final release. I stay on it deliberately — it is the documented, tutorial-matching way to hold a single
 * secret, it is effectively frozen (so "alpha" here means "last", not "churning"), and swapping in a small
 * Keystore-AES wrapper later touches only this file. The key never lives in the binary or the repo; it only
 * ever exists here, entered by the user.
 */
class SecretStore(context: Context) {

    // The application context, so this can be held for the app's lifetime without leaking an Activity.
    private val appContext = context.applicationContext

    // Built lazily: creating the master key and opening the encrypted file both touch the Keystore, which is
    // needless work if the key is never read (e.g. a preview build). `by lazy` is thread-safe by default.
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Store (or replace) the AEMET API key. An empty/blank string clears it, matching the Swift.
     *
     * Every whitespace character is stripped, not just the ends: a valid AEMET key is a JWT and never
     * contains internal whitespace, but email clients line-wrap it, so a pasted key can carry an internal
     * newline or space that survives end-trimming and would be persisted corrupt, causing a 401. Filtering
     * all whitespace makes the paste robust. Mirrors the iOS AuraKeychain.setAPIKey fix.
     */
    fun setApiKey(key: String) {
        val cleaned = key.filterNot { it.isWhitespace() }
        prefs.edit().apply {
            if (cleaned.isEmpty()) remove(API_KEY) else putString(API_KEY, cleaned)
        }.apply()
    }

    /** The stored AEMET API key, or null if none has been set. */
    fun apiKey(): String? = prefs.getString(API_KEY, null)

    private companion object {
        const val PREFS_FILE = "aura_secrets"
        const val API_KEY = "aemet-api-key"
    }
}
