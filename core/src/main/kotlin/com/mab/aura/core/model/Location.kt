package com.mab.aura.core.model

import kotlinx.serialization.Serializable

/**
 * A place Aura can show weather for: a Spanish municipality identified by its INE code, with the
 * coordinates needed for on-device sun times.
 *
 * AEMET forecasts are keyed by the 5-digit INE municipality code; the official text bulletins are keyed
 * by the 2-digit province code, which is simply the first two digits of the INE code, so no separate
 * province table is needed.
 *
 * Direct port of the `Location` struct in `Location.swift`. Swift's `Codable` becomes kotlinx's
 * [@Serializable] (this rides inside persisted state and is decoded from the saved-locations store).
 * Swift's `Identifiable`/`Hashable` are covered by the computed [id] and the data-class `equals`/`hashCode`.
 */
@Serializable
data class Location(
    /** 5-digit INE municipality code, e.g. "28079" (Madrid). */
    val ine: String,
    /** Municipality name, e.g. "Madrid". */
    val nombre: String,
    /** Province name, e.g. "Madrid" or "A Coruña". */
    val provincia: String,
    val latitude: Double,
    val longitude: Double,
) {
    /** Swift's `Identifiable` id. */
    val id: String get() = ine

    /** 2-digit INE province code, derived from the municipality code (e.g. "28" from "28079"). */
    val provinciaCode: String get() = ine.take(2)
}
