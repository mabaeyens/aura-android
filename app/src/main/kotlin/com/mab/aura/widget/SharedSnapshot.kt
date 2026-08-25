package com.mab.aura.widget

import android.content.Context
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.store.Settings
import com.mab.aura.store.SnapshotCache
import kotlinx.coroutines.flow.first

/**
 * Which cached snapshot the widget should draw. Android port of `SharedCache.resolve(preferredINE:)`.
 *
 * The widget never fetches — it re-renders whatever the app last stored (the app is the fetch hub). So this
 * reads the same [SnapshotCache] and [Settings] the app writes (no App Group is needed; the widget shares the
 * app process), and picks, in order: the widget's pinned location if it still has data, else the app's active
 * location, else the first cache entry — so an unconfigured widget tracks whatever the app is showing rather
 * than an arbitrary favourite.
 */
object SharedSnapshot {

    /**
     * The snapshot to show, or null when nothing has been cached yet (the widget then shows its empty state).
     * [preferredINE] is the tile's own pinned location (from `WidgetConfigActivity`); null, or a pin whose
     * place is no longer cached, falls through to the app's active location and then the first cache entry.
     */
    suspend fun resolve(context: Context, preferredINE: String? = null): WeatherSnapshot? {
        val cached = SnapshotCache(context).read()
        if (cached.isEmpty()) return null

        preferredINE?.let { ine -> cached.firstOrNull { it.ine == ine }?.let { return it } }

        val activeINE = Settings(context).activeINE.first()
        activeINE?.let { ine -> cached.firstOrNull { it.ine == ine }?.let { return it } }

        return cached.first()
    }
}
