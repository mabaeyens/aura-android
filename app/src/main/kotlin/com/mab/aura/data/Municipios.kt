package com.mab.aura.data

import android.content.Context
import com.mab.aura.core.geo.SpainCities
import com.mab.aura.core.model.Location
import kotlinx.serialization.json.Json

/**
 * The full Spanish municipality table for the "Añadir ubicación" search, bundled as an asset
 * (`assets/municipios.json`) generated once from AEMET's own `maestro/municipios` feed. Each entry is an
 * INE code, a name, a province and a centroid, the same fields AEMET forecasts and Aura's sun-time maths need.
 *
 * Why this is an `:app` asset and not `:core` code or a `:core` resource: it's the same split the hero art
 * uses. The *logic* (nearest-city, the small curated list) stays portable in `:core`'s [SpainCities]; the
 * *bulk data* (~8,100 rows) ships as an `:app` asset, decoded once here with `AssetManager`. It deserializes
 * straight into `:core`'s [Location] because the JSON uses Location's own field names (`ine`, `nombre`,
 * `provincia`, `latitude`, `longitude`).
 *
 * Fallback: if the asset is missing or unreadable — for instance before the one-time generator has run —
 * this returns [SpainCities.seed] so search still works with the 54 curated cities instead of going empty.
 * That is why wiring the search to this object never regresses the app: worst case it behaves exactly as the
 * old seed-only search did.
 */
object Municipios {

    // AEMET's own JSON can carry keys we don't model; tolerate them the same way the network client does.
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<Location>? = null

    /**
     * The full municipality table, parsed and cached on first call. The parse reads ~1 MB and builds ~8,100
     * objects, so it blocks briefly: call it off the main thread (the search screen does, via `Dispatchers.IO`).
     */
    fun all(context: Context): List<Location> =
        cache ?: synchronized(this) {
            cache ?: run {
                val loaded = runCatching {
                    context.assets.open("municipios.json").bufferedReader().use { reader ->
                        json.decodeFromString<List<Location>>(reader.readText())
                    }
                }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: SpainCities.seed
                loaded.also { cache = it }
            }
        }
}
