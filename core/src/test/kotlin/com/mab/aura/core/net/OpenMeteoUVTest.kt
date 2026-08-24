package com.mab.aura.core.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Exercises the ported `OpenMeteoUV` parse half against an OkHttp `MockWebServer` (the Kotlin stand-in for
 * the Swift tests' injected `URLSession`). The Swift file carries no unit tests for `OpenMeteoUV`, so these
 * pin the ported behaviour directly: the time/uv/clear-sky mapping, the `?? 0` / `?? uv` fallbacks, the
 * negative floor, the never-throws contract, and the request the feed URL builds.
 */
class OpenMeteoUVTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun client() = OpenMeteoUV(baseUrl = server.url("/v1/air-quality").toString())

    @Test
    fun fetch_mapsTimeUvAndClearSky() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"hourly":{"time":[1000,4600],"uv_index":[3.2,5.0],"uv_index_clear_sky":[4.0,6.5]}}""",
            ),
        )

        val slots = OpenMeteoUV(baseUrl = server.url("/v1/air-quality").toString())
            .fetch(latitude = 40.4, longitude = -3.7)

        assertEquals(2, slots.size)
        assertEquals(Instant.ofEpochSecond(1000), slots[0].date)
        assertEquals(3.2, slots[0].uv, 1e-9)
        assertEquals(4.0, slots[0].clearSky, 1e-9)
        assertEquals(6.5, slots[1].clearSky, 1e-9)
    }

    @Test
    fun fetch_fallsBackForNullAndShortArrays() = runTest {
        // Second uv_index is null → 0; clear-sky array is short at index 1 → falls back to that hour's uv (0).
        server.enqueue(
            MockResponse().setBody(
                """{"hourly":{"time":[1000,4600],"uv_index":[3.0,null],"uv_index_clear_sky":[4.0]}}""",
            ),
        )

        val slots = client().fetch(latitude = 40.4, longitude = -3.7)

        assertEquals(0.0, slots[1].uv, 1e-9)
        assertEquals(0.0, slots[1].clearSky, 1e-9)
    }

    @Test
    fun fetch_floorsNegativeValuesAtZero() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"hourly":{"time":[1000],"uv_index":[-1.5],"uv_index_clear_sky":[-0.5]}}""",
            ),
        )

        val slots = client().fetch(latitude = 40.4, longitude = -3.7)

        assertEquals(0.0, slots[0].uv, 1e-9)
        assertEquals(0.0, slots[0].clearSky, 1e-9)
    }

    @Test
    fun fetch_returnsEmptyOnNon200() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(client().fetch(latitude = 40.4, longitude = -3.7).isEmpty())
    }

    @Test
    fun fetch_returnsEmptyOnMalformedJson() = runTest {
        server.enqueue(MockResponse().setBody("not json"))
        assertTrue(client().fetch(latitude = 40.4, longitude = -3.7).isEmpty())
    }

    @Test
    fun fetch_buildsTheAirQualityRequest() = runTest {
        server.enqueue(MockResponse().setBody("""{"hourly":{"time":[],"uv_index":[],"uv_index_clear_sky":[]}}"""))

        client().fetch(latitude = 40.4, longitude = -3.7)

        val path = server.takeRequest().path!!
        assertTrue(path.startsWith("/v1/air-quality"))
        assertTrue(path.contains("latitude=40.4"))
        assertTrue(path.contains("longitude=-3.7"))
        assertTrue(path.contains("timeformat=unixtime"))
        assertTrue(path.contains("timezone=auto"))
        assertTrue(path.contains("forecast_days=2"))
    }
}
