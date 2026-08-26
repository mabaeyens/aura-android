package com.mab.aura.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One of AEMET's 15 regional weather radars. Each regional image is a ~240 km-radius circle centred on the
 * radar site, so picking the nearest site to a location gives an image that's already "local" — no cropping
 * or georeferencing needed. Coordinates are the radar cities (close enough for nearest-site selection; the
 * true antenna sites differ by a few km).
 *
 * Direct port of `RadarSite` in `RadarSite.swift`. Swift's `Sendable, Hashable` map to a plain Kotlin `data
 * class` (immutable, with structural `equals`/`hashCode`). The 15-site list and coordinates are kept exact.
 */
data class RadarSite(
    /** AEMET regional radar code, e.g. "ma" for Madrid. */
    val code: String,
    /** Human name for the card subtitle, e.g. "Madrid". */
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        /** All 15 regional radars. */
        val all: List<RadarSite> = listOf(
            RadarSite("am", "Almería", 36.83, -2.46),
            RadarSite("sa", "Asturias", 43.43, -6.30),
            RadarSite("pm", "Illes Balears", 39.57, 2.65),
            RadarSite("ba", "Barcelona", 41.39, 2.16),
            RadarSite("cc", "Cáceres", 39.47, -6.37),
            RadarSite("co", "A Coruña", 43.37, -8.40),
            RadarSite("ma", "Madrid", 40.42, -3.70),
            RadarSite("ml", "Málaga", 36.72, -4.42),
            RadarSite("mu", "Murcia", 37.99, -1.13),
            RadarSite("vd", "Palencia", 42.01, -4.53),
            RadarSite("ca", "Las Palmas", 28.10, -15.41),
            RadarSite("se", "Sevilla", 37.39, -5.99),
            RadarSite("va", "Valencia", 39.47, -0.38),
            RadarSite("ss", "Vizcaya", 43.26, -2.93),
            RadarSite("za", "Zaragoza", 41.65, -0.89),
        )

        /** The nearest radar site to a location (great-circle). Never null — the list is non-empty. */
        fun nearest(latitude: Double, longitude: Double): RadarSite =
            all.minBy { haversine(latitude, longitude, it.latitude, it.longitude) }

        /** Great-circle distance in kilometres (haversine). Same form as `SpainCities.haversine`. */
        private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val p = Math.PI / 180
            val dLat = (lat2 - lat1) * p
            val dLon = (lon2 - lon1) * p
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * p) * cos(lat2 * p) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * asin(min(1.0, sqrt(a)))
        }
    }
}
