package com.mab.aura.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Enqueues the periodic [RefreshWorker]. Called on every app launch, but the work is enqueued as *unique* with
 * [ExistingPeriodicWorkPolicy.KEEP], so relaunching never resets the schedule or stacks duplicate work — the
 * first enqueue wins and subsequent launches are no-ops. WorkManager persists the schedule across reboots.
 *
 * Cadence is 3 hours, matching the iOS widget reload interval, and only runs on a network with the battery not
 * low, so the background refresh stays within the app's idle-by-default battery discipline (see the plan's
 * BATTERY notes). The exact firing time is WorkManager's to choose within each interval; it batches work to
 * save power, so treat 3 hours as "about every 3 hours", not a precise timer.
 */
object WeatherRefreshScheduler {

    private const val WORK_NAME = "aura-weather-refresh"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<RefreshWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
