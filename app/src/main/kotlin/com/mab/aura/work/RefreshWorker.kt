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

        // Set only by the thin-retry request (see [WeatherRefreshScheduler.scheduleThinRetry]): force past the
        // 1-hour gate, since the snapshot we're retrying was just written and is younger than an hour.
        val force = inputData.getBoolean(WeatherRefreshScheduler.KEY_FORCE, false)

        return try {
            repository.refresh(favourites, force = force)
            // On a forced thin-retry, if any favourite still lacks current-hour data the feed is still degraded:
            // ask WorkManager to back off and try again, rather than leaving a thin widget until the next
            // periodic run. The ordinary periodic pass never does this — a thin result there just waits out the
            // normal cadence and the app's own retry, so a healthy widget isn't churned on every cycle.
            if (force && favourites.any { repository.cachedSnapshot(it.ine)?.hasCurrentHourData != true }) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            // A transient network/AEMET failure: let WorkManager back off and try again, keeping the last
            // good cache on the widget in the meantime.
            Result.retry()
        }
    }
}
