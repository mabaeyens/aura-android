package com.mab.aura.core.net

import com.mab.aura.core.model.UVHourSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import kotlin.math.max

/**
 * CAMS (Copernicus Atmosphere Monitoring Service) hourly UV forecast, fetched per-coordinate from
 * Open-Meteo's free Air Quality API. This is the hourly UV index AEMET's OpenData doesn't publish —
 * AEMET gives only a forecast clear-sky daily maximum (see `UVIForecast`/`UVIndex`), so Aura keeps that
 * as the official headline and shows this hourly curve alongside it.
 *
 * Attribution (required): data © CAMS / Copernicus (CC BY 4.0) via Open-Meteo — credit both, next to the
 * AEMET + MITECO line. The free endpoint is non-commercial (≤10k calls/day per client IP); Aura calls it
 * directly from each device, so every install has its own budget and the cap is never in reach.
 *
 * Direct port of `OpenMeteoUV` in `UVHourly.swift` (the [UVHourSlot] model half is already in `:core`).
 * Swift models this as a namespacing `enum` with static methods and an injected `URLSession`; here it is a
 * class whose [baseUrl]/[httpClient] are injectable, mirroring [AemetClient] so tests can point it at an
 * OkHttp `MockWebServer`. Swift's `async` method becomes a `suspend` function. Unlike [AemetClient] this
 * never throws: any failure (bad status, decode error, network) just yields an empty list, so a UV outage
 * hides the hourly curve instead of surfacing an error, exactly as on the Swift side.
 */
class OpenMeteoUV(
    private val baseUrl: String = DEFAULT_BASE,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        const val DEFAULT_BASE = "https://air-quality-api.open-meteo.com/v1/air-quality"
    }

    /** The Open-Meteo response: two parallel arrays of hourly UV values keyed by a shared time axis. */
    @Serializable
    private data class Response(val hourly: Hourly) {
        @Serializable
        data class Hourly(
            /** Hour instants as UTC epoch seconds (`timeformat=unixtime`). */
            val time: List<Long>,
            /** Forecast UV index per hour (cloud effect included). Elements can be null. */
            @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
            /** Clear-sky UV index per hour. Elements can be null. */
            @SerialName("uv_index_clear_sky") val uvIndexClearSky: List<Double?> = emptyList(),
        )
    }

    /**
     * Today + tomorrow's hourly UV for a point, or `[]` on any failure. Times come back as UTC epochs and
     * the day-bucketing is done in the location's own zone (`timezone=auto`), so the first 24 slots are
     * already that location's local day (see [com.mab.aura.core.model.todaySlots]).
     */
    suspend fun fetch(latitude: Double, longitude: Double): List<UVHourSlot> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", latitude.toString())
            .addQueryParameter("longitude", longitude.toString())
            .addQueryParameter("hourly", "uv_index,uv_index_clear_sky")
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "2")
            .build()

        // Any failure collapses to an empty list: a non-200, a network error, or a decode failure all just
        // hide the hourly UV curve. OkHttp's execute() is blocking, so it runs on the IO dispatcher, and the
        // body must be read inside use {} before the response closes.
        return try {
            val body = withContext(Dispatchers.IO) {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.code == 200) response.body?.string() else null
                }
            } ?: return emptyList()

            val hourly = json.decodeFromString(Response.serializer(), body).hourly
            hourly.time.mapIndexed { i, epochSeconds ->
                // Missing (short array) or explicitly-null values fall back the way Swift's `?? 0` / `?? uv`
                // do; both indices are floored at 0 so a negative model artefact never shows.
                val uv = hourly.uvIndex.getOrNull(i) ?: 0.0
                val clear = hourly.uvIndexClearSky.getOrNull(i) ?: uv
                UVHourSlot(
                    date = Instant.ofEpochSecond(epochSeconds),
                    uv = max(0.0, uv),
                    clearSky = max(0.0, clear),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
