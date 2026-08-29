package com.mab.aura.core.net

import com.mab.aura.core.model.MunicipioForecast
import com.mab.aura.core.model.MunicipioHourly
import com.mab.aura.core.model.StationObservation
import com.mab.aura.core.model.UVIForecast
import com.mab.aura.core.model.WeatherAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.Instant
import kotlin.math.pow

/** The typed errors [AemetClient] can throw. Swift models this as a single `ClientError` enum. */
sealed class AemetClientException(message: String) : Exception(message) {
    /** No API key was configured. */
    object MissingApiKey : AemetClientException("missing API key")
    /** A non-200, non-429 HTTP status. */
    class Http(val code: Int) : AemetClientException("HTTP $code")
    /** AEMET's envelope came back without a usable `datos` URL. */
    class AemetStatus(val estado: Int, val descripcion: String) :
        AemetClientException("AEMET status $estado: $descripcion")
    /** The envelope or a payload could not be decoded. */
    class Decoding(val detail: String) : AemetClientException("decoding: $detail")
    /** Still rate-limited (429) after exhausting retries. */
    object RateLimited : AemetClientException("rate limited")
}

/**
 * Minimal client for the AEMET OpenData API.
 *
 * AEMET uses a two-call model: the first request returns a small JSON envelope carrying a temporary `datos`
 * URL, and a second request to that URL returns the actual payload. Payloads are usually UTF-8 but some
 * legacy products are ISO-8859-1, so bytes are charset-detected before decoding. The API key rides as the
 * `api_key` query parameter.
 *
 * Direct port of the Swift `AEMETClient`. The Swift version injects a `URLSession` for testing; here the
 * [baseUrl] and [httpClient] are injectable instead (OkHttp's `MockWebServer` in tests). Swift's `async`
 * throwing methods become `suspend` functions that throw [AemetClientException].
 *
 * The plain-text community-bulletin helpers (`comunidadBulletin` and the `AemetBulletinParser`) live in the
 * sibling `AemetBulletin.kt`, built on the [fetchText] engine here.
 */
class AemetClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE,
    // The keyless observation RSS notifier (a full absolute URL on a different host path from [baseUrl], so it
    // is not built through the two-call API engine). Overridable so tests can point it at a mock server.
    private val observacionRssUrl: String = OBSERVACION_RSS_URL,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val pacer: RequestPacer = RequestPacer.shared,
    private val json: Json = Json { ignoreUnknownKeys = true },
    // Backoff before retrying a 429, keyed by attempt number (1, then 2). Injectable so tests need not wait;
    // the app uses AEMET's 2 s then 4 s exponential backoff.
    private val retryBackoffMillis: (Int) -> Long = { attempt -> (2.0.pow(attempt) * 1000).toLong() },
) {
    companion object {
        const val DEFAULT_BASE = "https://opendata.aemet.es/opendata/api"

        /**
         * AEMET's keyless observation RSS. A plain static-host GET (no api_key, no envelope), served from
         * `/rss/`, not the `/opendata/api` product base. See [observacionRssUpdated].
         */
        const val OBSERVACION_RSS_URL =
            "https://opendata.aemet.es/rss/obsconv_hh_opendata_todos_RSS.xml"
    }

    /** The envelope returned by every first call. */
    @kotlinx.serialization.Serializable
    private data class Envelope(
        val estado: Int? = null,
        val descripcion: String? = null,
        val datos: String? = null,
        val metadatos: String? = null,
    )

    // --- The two-call engine -------------------------------------------------------------------------

    /** Runs the full two-call model for [path] and decodes the payload with [deserializer]. */
    suspend fun <T> fetch(path: String, deserializer: DeserializationStrategy<T>): T {
        val envelope = requestEnvelope(path)
        val bytes = perform(datosUrl(envelope))
        return decodePayload(bytes, deserializer)
    }

    /**
     * Runs the two-call model for [path] where the payload is a plain-text bulletin rather than JSON. AEMET
     * serves these as UTF-8; falls back to Latin-1 for any legacy endpoint.
     */
    suspend fun fetchText(path: String): String {
        val envelope = requestEnvelope(path)
        val bytes = perform(datosUrl(envelope))
        return decodeText(bytes) ?: throw AemetClientException.Decoding("text payload: undecodable")
    }

    /** Runs the two-call model for [path] and returns the raw payload bytes (for binary products). */
    suspend fun fetchBinary(path: String): ByteArray {
        val envelope = requestEnvelope(path)
        return perform(datosUrl(envelope))
    }

    private suspend fun requestEnvelope(path: String): Envelope {
        if (apiKey.isEmpty()) throw AemetClientException.MissingApiKey
        // Swift concatenates base + path as strings (the datos URL later is absolute); mirror that rather than
        // HttpUrl.resolve, which would drop the "/opendata/api" prefix for a path starting with "/".
        val url = (baseUrl + path).toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .build()
        val bytes = perform(url)
        return try {
            json.decodeFromString(Envelope.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw AemetClientException.Decoding("envelope: ${e.message}")
        }
    }

    /** The `datos` URL from an envelope, or an [AemetClientException.AemetStatus] when it is missing/invalid. */
    private fun datosUrl(envelope: Envelope): HttpUrl =
        envelope.datos?.toHttpUrlOrNull()
            ?: throw AemetClientException.AemetStatus(envelope.estado ?: -1, envelope.descripcion ?: "no datos url")

    /**
     * Performs one GET, paced through the shared limiter and retried on a 429 with exponential backoff. Both
     * calls in the two-step model funnel through here, so every product counts against the same per-key budget.
     * Returns the body on 200; throws [AemetClientException.RateLimited] after exhausting retries, or
     * [AemetClientException.Http] for anything else.
     */
    private suspend fun perform(url: HttpUrl): ByteArray {
        var attempt = 0
        while (true) {
            pacer.waitForSlot()
            val request = Request.Builder().url(url).build()
            // OkHttp's execute() is blocking, so run it on the IO dispatcher; the response body must be read
            // inside use {} before the response closes.
            val result = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    val body = if (response.code == 200) (response.body?.bytes() ?: ByteArray(0)) else null
                    response.code to body
                }
            }
            val code = result.first
            if (code == 200) return result.second ?: ByteArray(0)
            if (code == 429 && attempt < 2) {
                attempt += 1
                delay(retryBackoffMillis(attempt))
                continue
            }
            if (code == 429) throw AemetClientException.RateLimited
            throw AemetClientException.Http(code)
        }
    }

    // --- Payload decoding ----------------------------------------------------------------------------

    private fun <T> decodePayload(bytes: ByteArray, deserializer: DeserializationStrategy<T>): T {
        val text = decodeText(bytes) ?: throw AemetClientException.Decoding("payload: undecodable bytes")
        return try {
            json.decodeFromString(deserializer, text)
        } catch (e: Exception) {
            throw AemetClientException.Decoding("payload: ${e.message}")
        }
    }

    /**
     * Turns payload bytes into a String. AEMET usually serves UTF-8; some legacy products are ISO-8859-1.
     * Decode strictly as UTF-8 first — reporting malformed bytes rather than silently substituting U+FFFD,
     * which is what would let a Latin-1 payload slip through mis-decoded — and fall back to Latin-1, which maps
     * every byte. (The Swift version conflates charset and JSON-validity by retrying the whole decode; deciding
     * the charset up front here is equivalent and clearer.)
     */
    private fun decodeText(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            bytes.toString(Charsets.ISO_8859_1)
        }

    // --- Typed endpoints -----------------------------------------------------------------------------

    /** Daily forecast for an INE municipality code (e.g. "28079" for Madrid). */
    suspend fun municipioDiaria(ine: String): MunicipioForecast {
        val list = fetch(
            "/prediccion/especifica/municipio/diaria/$ine",
            ListSerializer(MunicipioForecast.serializer()),
        )
        return list.firstOrNull() ?: throw AemetClientException.Decoding("empty forecast array")
    }

    /** Hourly forecast for an INE municipality code — powers the "now" reading and the hourly strip. */
    suspend fun municipioHoraria(ine: String): MunicipioHourly {
        val list = fetch(
            "/prediccion/especifica/municipio/horaria/$ine",
            ListSerializer(MunicipioHourly.serializer()),
        )
        return list.firstOrNull() ?: throw AemetClientException.Decoding("empty hourly array")
    }

    /**
     * Forecast daily-max UV index for every provincial capital, in one call. [dia] 0 = today … 4. One fetch
     * serves every location; resolve per location by INE with `UVIndex.pick`. The payload is a single JSON
     * object (not the array most `/prediccion` products use).
     */
    suspend fun uviCities(dia: Int = 0): List<UVIForecast.City> =
        fetch("/prediccion/especifica/uvi/$dia", UVIForecast.serializer()).ciudad

    /**
     * Every recent surface observation from AEMET's conventional station network, in one call — many records
     * per station across the country. One fetch serves every location; resolve per location to the nearest
     * recent station with `List<StationObservation>.nearest(to:)`. Powers the "Estación de observación" card.
     */
    suspend fun observacionTodas(): List<StationObservation> =
        fetch("/observacion/convencional/todas", ListSerializer(StationObservation.serializer()))

    /**
     * When AEMET last refreshed the conventional-observation dataset, from the keyless RSS notifier
     * ([OBSERVACION_RSS_URL]), or null when the feed is unreachable or unparseable. A single keyless GET (no
     * api_key, no two-call envelope), so the refresh path can decide whether [observacionTodas] is worth
     * calling without spending a keyed request to find out. It still funnels through [perform], so it gets the
     * same 429 backoff and, conservatively, counts against the shared pacer — one cheap keyless GET that, when
     * the marker has not advanced, saves the far larger keyed observation download. The returned value is a
     * publish time (~30 min past the hour), a different clock from the observation `fint`; never compare them.
     */
    suspend fun observacionRssUpdated(): Instant? =
        ObservationRss.latestUpdate(perform(observacionRssUrl.toHttpUrl()))

    /**
     * The latest regional radar image (a ~240 km-radius reflectivity frame). Raw image bytes (GIF/PNG); pick
     * the site with `RadarSite.nearest`. Updates every ~10 min.
     */
    suspend fun radarRegional(code: String): ByteArray =
        fetchBinary("/red/radar/regional/$code")

    /**
     * The latest observed surface analysis chart (isobars, high/low centres and fronts over Europe and the
     * North Atlantic). Raw image bytes: a single-frame GIF stored rotated portrait, which the caller decodes
     * and rotates 90° clockwise into a wide landscape map. AEMET reissues it every 12 h, so the app fetches
     * it at most once per 12 h. Mirrors [radarRegional]; the two-call envelope→`datos` model and pacing are
     * handled by [fetchBinary].
     */
    suspend fun surfaceAnalysis(): ByteArray =
        fetchBinary("/mapasygraficos/analisis")

    /**
     * Active meteorological warnings for an AEMET avisos area (a `.tar` of CAP-XML files). [area] is a
     * two-digit community code (from `AvisoArea.forProvincia`). The payload is unpacked with [TarReader]
     * and each `.xml` member parsed by [CAPParser]; filter the result to a location by province with
     * `List<WeatherAlert>.topActive`.
     */
    suspend fun avisos(area: String): List<WeatherAlert> {
        val tar = fetchBinary("/avisos_cap/ultimoelaborado/area/$area")
        return TarReader.files(tar)
            .filter { it.first.endsWith(".xml") }
            .flatMap { CAPParser.parse(it.second) }
    }
}
