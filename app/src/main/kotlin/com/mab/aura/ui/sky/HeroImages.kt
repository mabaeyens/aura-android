package com.mab.aura.ui.sky

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Loads the hero background art bundled under `assets/heroes/` — the downscaled WebP port of iOS's 8×6
 * grid (condition × time-of-day), one flat name per file (e.g. `clear_noon`, `city_stormy_dusk`). This is
 * the "Appendix A" delivery half that `HeroBackground` (in `:core`) only ever computed a *name* for; the
 * bytes finally live here.
 *
 * Kept in `assets/` rather than `res/drawable/` on purpose: the resolver in `HeroBackground` works by asset
 * *name*, and `AssetManager` gives a clean name-keyed lookup with no `R`-id indirection or density mangling
 * (the art is full-bleed, not per-density). The masters ship phone-sized (~1080 px wide), so decoding is
 * cheap and no `inSampleSize` downsampling is needed.
 */
object HeroImages {

    private const val DIR = "heroes"

    // Which hero names actually shipped, discovered once from the asset directory. HeroBackground.resolve is
    // handed this set so it only ever returns a name that has bytes behind it, falling back to the procedural
    // sky otherwise.
    @Volatile
    private var namesCache: Set<String>? = null

    // A tiny most-recent cache: only one hero is on screen at a time, but a refresh or a family toggle can
    // ask for a second, and a decoded 1080×2340 bitmap is a few MB, so a couple are worth keeping.
    private val bitmapCache = object : LinkedHashMap<String, ImageBitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>?): Boolean = size > 3
    }

    /** The set of shipped hero asset names (without the `.webp` extension), for `HeroBackground.resolve`. */
    fun availableNames(context: Context): Set<String> =
        namesCache ?: synchronized(this) {
            namesCache ?: run {
                val list = runCatching {
                    context.assets.list(DIR)?.mapNotNull { file ->
                        file.removeSuffix(".webp").takeIf { it != file }
                    }
                }.getOrNull().orEmpty().toSet()
                list.also { namesCache = it }
            }
        }

    /**
     * Decode one hero by name, or null if it isn't present or fails to decode (the caller then shows the
     * procedural sky). Decoding is blocking, so call this off the main thread.
     */
    fun load(context: Context, name: String): ImageBitmap? {
        synchronized(bitmapCache) { bitmapCache[name]?.let { return it } }
        val bitmap = runCatching {
            context.assets.open("$DIR/$name.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val image = bitmap.asImageBitmap()
        synchronized(bitmapCache) { bitmapCache[name] = image }
        return image
    }
}
