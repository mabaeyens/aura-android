package com.mab.aura.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
    private const val THIN_RETRY_WORK = "aura-weather-thin-retry"
    private const val WIDGET_KICK_WORK = "aura-weather-widget-kick"

    /** Input flag telling [RefreshWorker] to force past the 1-hour freshness gate (used by the thin retry, so
     *  the just-written thin snapshot doesn't short-circuit its own retry). */
    const val KEY_FORCE = "force"

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

    /**
     * A one-shot "refresh now", used when a widget is first placed (see [com.mab.aura.widget.AuraWidgetReceiver]).
     * A freshly placed tile would otherwise sit on the empty state or a stale cache until either the next app
     * open or the next ~3 h periodic pass, since the app schedules the periodic work only in `MainActivity`.
     *
     * Deliberately *not* forced: it goes through [RefreshWorker], which obeys the repository's 1-hour freshness
     * gate, so placing a tile moments after the app already refreshed makes zero network calls (it just re-reads
     * the current cache). Unique with KEEP so several tiles placed together, or a placement plus a resize, all
     * coalesce into one request rather than firing a fetch each. Network-constrained; a transient failure backs
     * off exactly like the periodic worker. If there is no key or no favourites yet, the worker succeeds quietly
     * and the tile keeps its empty state.
     */
    fun refreshNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WIDGET_KICK_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Enqueue a one-shot forced retry after a thin snapshot was written (a fetch that came back with no
     * current-hour data on a cold start, so the 1-hour gate would otherwise pin the widget and screen to that
     * thin state). Not periodic and deliberately not expedited: on minSdk 26 an expedited [CoroutineWorker]
     * needs a foreground-service notification, far too heavy for a silent retry, and a plain one-time request
     * with only a network constraint already runs within seconds. If the retry itself still comes back thin,
     * [RefreshWorker] returns [androidx.work.ListenableWorker.Result.retry] and this request's exponential
     * backoff drives the next attempt. Unique with KEEP so several thin writes in one cycle coalesce into one.
     */
    fun scheduleThinRetry(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_FORCE to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            THIN_RETRY_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
