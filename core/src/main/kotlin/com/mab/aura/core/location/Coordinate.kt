package com.mab.aura.core.location

/**
 * A geographic point: the boundary type `:core` speaks in for location. `:app` acquires a real device fix
 * (via the platform `LocationManager`, never Google Play Services) and hands `:core` one of these; `:core`
 * itself stays free of any `android.*` import, exactly as AuraKit stays platform-free on iOS.
 *
 * `SolarTimes.compute` still takes plain `latitude`/`longitude` doubles today; this gives the acquisition
 * layer a single value to pass around, and a later step can migrate `compute` to take a [Coordinate]
 * directly.
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)
