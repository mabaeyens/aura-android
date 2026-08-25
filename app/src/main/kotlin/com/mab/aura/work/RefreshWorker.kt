package com.mab.aura.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mab.aura.data.WeatherRepository
import com.mab.aura.store.Settings
import kotlinx.coroutines.flow.first

/**
 * The deferred background refresh that keeps the Home Screen widget current while the app is closed. Android
 * equivalent of the iOS widget's timeline reload cadence, but here it actually re-fetches (iOS relied on the
 * app running to fill the cache); [WorkManager][androidx.work.WorkManager] runs it on a battery-friendly
 * schedule that survives Doze and OEM background limits, which a bare service would not.
 *
 * It refreshes every saved favourite. That is bounded and cheap: [WeatherRepository.refresh] skips any
 * location cached within the last hour and obeys AEMET's rate limit, so a 3-hour cadence over a handful of
 * favourites is a few calls at most. The repository writes the cache and nudges the widget itself, so this
 * worker only has to ask.
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val favourites = Settings(applicationContext).favourites.first()
        // Nothing pinned yet, or no AEMET key entered — there is nothing to fetch. Succeed quietly rather than
        // retry; the next successful app open is what sets these up.
        if (favourites.isEmpty()) return Result.success()

        val repository = WeatherRepository(applicationContext)
        if (!repository.hasApiKey()) return Result.success()

        return try {
            repository.refresh(favourites)
            Result.success()
        } catch (e: Exception) {
            // A transient network/AEMET failure: let WorkManager back off and try again, keeping the last
            // good cache on the widget in the meantime.
            Result.retry()
        }
    }
}
