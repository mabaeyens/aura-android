package com.mab.aura.ui.sky

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Loads the hero background art bundled under `assets/`, the downscaled WebP port of iOS's hero grids (one
 * flat name per file, e.g. `clear_noon`, `city_stormy_dusk`, `wide_landscape_day`). This is the "Appendix A"
 * delivery half that `HeroBackground` (in `:core`) only ever computed a *name* for; the bytes finally live here.
 *
 * Two grids ship: the tall **portrait** art for the phone's `AuraSky` (`assets/heroes/`) and the 4:3 **wide**
 * art for the Glance widget (`assets/heroes-wide/`). Both are addressed by asset *name*, which is why they sit
 * in `assets/` rather than `res/drawable/`: `AssetManager` gives a clean name-keyed lookup with no `R`-id
 * indirection or density mangling (the art is full-bleed, not per-density).
 */
object HeroImages {

    private val portrait = Store("heroes")
    private val wide = Store("heroes-wide")

    /** The shipped **portrait** hero names (no extension), for `HeroBackground.resolve`. */
    fun availableNames(context: Context): Set<String> = portrait.names(context)

    /** Decode one **portrait** hero as an [ImageBitmap] (for the Compose `AuraSky`), or null. Blocking. */
    fun load(context: Context, name: String): ImageBitmap? = portrait.bitmap(context, name)?.asImageBitmap()

    /** The shipped **wide** hero names (no extension), for `HeroBackground.wideName`/`wideBaseName`. */
    fun availableWideNames(context: Context): Set<String> = wide.names(context)

    /** Decode one **wide** hero as a plain [Bitmap] (for the Glance widget's `ImageProvider`), or null. Blocking. */
    fun loadWide(context: Context, name: String): Bitmap? = wide.bitmap(context, name)

    /** One asset directory with its own name list + a tiny most-recent bitmap cache. */
    private class Store(private val dir: String) {

        @Volatile
        private var namesCache: Set<String>? = null

        // Only one hero is on screen at a time, but a refresh or a family toggle can ask for a second, and a
        // decoded bitmap is a few MB, so keep the last few rather than re-decoding on every update.
        private val bitmapCache = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>?): Boolean = size > 3
        }

        fun names(context: Context): Set<String> =
            namesCache ?: synchronized(this) {
                namesCache ?: run {
                    val list = runCatching {
                        context.assets.list(dir)?.mapNotNull { file ->
                            file.removeSuffix(".webp").takeIf { it != file }
                        }
                    }.getOrNull().orEmpty().toSet()
                    list.also { namesCache = it }
                }
            }

        fun bitmap(context: Context, name: String): Bitmap? {
            synchronized(bitmapCache) { bitmapCache[name]?.let { return it } }
            val bitmap = runCatching {
                context.assets.open("$dir/$name.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: return null
            synchronized(bitmapCache) { bitmapCache[name] = bitmap }
            return bitmap
        }
    }
}
