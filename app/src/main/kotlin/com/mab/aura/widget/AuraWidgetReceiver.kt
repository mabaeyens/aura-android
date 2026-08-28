package com.mab.aura.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.mab.aura.work.WeatherRefreshScheduler

/**
 * The `AppWidgetProvider` the system talks to for Aura's Home Screen widget. Glance's base receiver forwards
 * the framework's broadcasts (add, update, resize, delete) to [AuraGlanceWidget]; all it has to declare is
 * which widget it hosts. Registered in the manifest against `@xml/aura_widget_info`.
 */
class AuraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraGlanceWidget()

    /**
     * Fired once when the *first* Aura tile is placed (not on later adds or resizes). The widget renders purely
     * from the shared cache and never fetches, so without this a tile placed on a phone where the app is rarely
     * opened would depend entirely on `MainActivity` having enqueued the periodic refresh, and a brand-new tile
     * would sit empty until the next app open. Here the widget takes both jobs itself:
     *
     *  - [WeatherRefreshScheduler.schedule] enqueues the ~3 h periodic background refresh. It is unique + KEEP,
     *    so if the app already scheduled it this is a no-op; if the app was never opened, the tile still keeps
     *    itself current.
     *  - [WeatherRefreshScheduler.refreshNow] kicks a single non-forced fetch so the tile fills within seconds
     *    of placement instead of waiting for the next app open or periodic pass. It obeys the repository's
     *    1-hour freshness gate, so it costs nothing when the cache is already current.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherRefreshScheduler.schedule(context)
        WeatherRefreshScheduler.refreshNow(context)
    }
}
