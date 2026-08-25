package com.mab.aura.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.mab.aura.core.location.Coordinate
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * The one place in Aura that touches Android location APIs. It reads a single current fix from the platform
 * [LocationManager] (via [LocationManagerCompat], never Google Play Services, so Aura works the same on
 * GMS-less devices and on the emulator) and hands `:core` a plain [Coordinate].
 *
 * On iOS this is `CLLocationManager`; here it is the AOSP `LocationManager`. The permission *request* UI is
 * the caller's job (a Compose permission launcher in the screen) — this provider only reads the current
 * grant and reports it, so acquisition and UI stay separate. Every outcome is a [LocationResult]: the three
 * no-fix states (permission denied, services off, no fix) are distinct so the UI can show a distinct message
 * for each, per `specs/location.md`.
 */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** Whether the coarse-location grant is currently held. The UI checks this to decide whether to prompt. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Acquire the device's current position, or report why it isn't available. Suspends until the platform
     * delivers a fix, its timeout elapses, or the caller's coroutine is cancelled (which cancels the request).
     */
    suspend fun current(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) return LocationResult.ServicesOff

        val provider = coarseProvider() ?: return LocationResult.ServicesOff

        // getCurrentLocation is callback-based; bridge it to a suspend function. A CancellationSignal ties the
        // in-flight request to the coroutine, so navigating away or timing out stops the GPS/network work.
        val fix = suspendCancellableCoroutine { cont ->
            val signal = androidx.core.os.CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            val executor: Executor = ContextCompat.getMainExecutor(appContext)
            LocationManagerCompat.getCurrentLocation(locationManager, provider, signal, executor) { location ->
                cont.resume(location)
            }
        }

        val coordinate = fix?.toCoordinate() ?: lastKnown()
        return if (coordinate != null) LocationResult.Available(coordinate) else LocationResult.NoFix
    }

    /**
     * The best enabled coarse-capable provider. FUSED (API 31+) is the AOSP fused provider — not the Google
     * one — and honours a coarse grant; below that, NETWORK. GPS is skipped on purpose: it needs the FINE
     * permission Aura doesn't request. Null when neither is enabled.
     */
    private fun coarseProvider(): String? {
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return preferred.firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    /** Last-resort fallback when no live fix arrives (indoors, cold start): the freshest cached fix, if any. */
    private fun lastKnown(): Coordinate? {
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return providers
            .mapNotNull { p -> runCatching { locationManager.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.toCoordinate()
    }

    private fun android.location.Location.toCoordinate() = Coordinate(latitude, longitude)
}

/** The outcome of a location request: a fix, or one of the three distinct no-fix reasons. */
sealed interface LocationResult {
    data class Available(val coordinate: Coordinate) : LocationResult

    /** The coarse-location permission has not been granted (or was denied). The UI should prompt or explain. */
    data object PermissionDenied : LocationResult

    /** Location services are switched off system-wide, or no coarse provider is enabled. */
    data object ServicesOff : LocationResult

    /** Permission and services are fine, but no fix arrived and there is no last-known location to fall back on. */
    data object NoFix : LocationResult
}
