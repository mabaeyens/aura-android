package com.mab.aura.core.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the ported `AemetClient` two-call engine against an OkHttp `MockWebServer`: the Kotlin stand-in
 * for the Swift tests' injected `URLSession`. Responses are served FIFO, so a two-call fetch is set up by
 * enqueuing the envelope first and the payload second. Retry backoff is injected as zero so the 429 paths
 * don't wait.
 */
class AemetClientTest {

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

    /** A client pointed at the mock server, with a private pacer and instant retries. */
    private fun client(apiKey: String = "KEY") = AemetClient(
        apiKey = apiKey,
        baseUrl = server.url("/opendata/api").toString(),
        pacer = RequestPacer(limit = 1000),
        retryBackoffMillis = { 0 },
    )

    private fun envelopePointingToDatos(estado: Int = 200): MockResponse =
        MockResponse().setBody("""{"estado":$estado,"datos":"${server.url("/datos")}"}""")

    @Test
    fun uviCities_runsTheTwoCallModelAndReturnsCities() = runTest {
        server.enqueue(envelopePointingToDatos())
        server.enqueue(
            MockResponse().setBody(
                """{"FECHA_VALIDEZ":"2026-08-24","CIUDAD":[{"id":"28079","valor":"Madrid","uv":"8"}]}""",
            ),
        )

        val cities = client().uviCities(dia = 0)

        assertEquals(1, cities.size)
        assertEquals("28079", cities[0].id)
        assertEquals("8", cities[0].uv)
        // First call carries the path + api_key; the second call hits the envelope's datos URL.
        val first = server.takeRequest()
        assertTrue(first.path!!.startsWith("/opendata/api/prediccion/especifica/uvi/0"))
        assertTrue(first.path!!.contains("api_key=KEY"))
        assertEquals("/datos", server.takeRequest().path)
    }

    @Test
    fun municipioDiaria_returnsTheFirstElementOfTheArray() = runTest {
        server.enqueue(envelopePointingToDatos())
        server.enqueue(
            MockResponse().setBody(
                """[{"nombre":"Madrid","provincia":"Madrid","prediccion":{"dia":[{"fecha":"2026-08-28"}]}}]""",
            ),
        )

        val forecast = client().municipioDiaria("28079")

        assertEquals("Madrid", forecast.nombre)
        assertEquals("2026-08-28", forecast.prediccion.dia.single().fecha)
    }

    @Test
    fun fetchText_fallsBackToLatin1WhenThePayloadIsNotUtf8() = runTest {
        server.enqueue(envelopePointingToDatos())
        // "Predicción" encoded as ISO-8859-1: the "ó" is a single 0xF3 byte, invalid as UTF-8.
        val latin1 = "Predicción".toByteArray(Charsets.ISO_8859_1)
        server.enqueue(MockResponse().setBody(Buffer().write(latin1)))

        assertEquals("Predicción", client().fetchText("/prediccion/ccaa/hoy/mad"))
    }

    @Test
    fun perform_retriesOn429ThenSucceeds() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(envelopePointingToDatos())
        server.enqueue(MockResponse().setBody("hola"))

        assertEquals("hola", client().fetchText("/prediccion/ccaa/hoy/mad"))
        assertEquals(3, server.requestCount) // 429, then the retried envelope, then the payload
    }

    @Test
    fun perform_throwsRateLimitedAfterExhaustingRetries() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429)) } // initial + 2 retries

        assertThrows(AemetClientException.RateLimited::class.java) {
            runTest { client().fetchText("/prediccion/ccaa/hoy/mad") }
        }
    }

    @Test
    fun missingApiKey_throwsBeforeMakingAnyRequest() {
        assertThrows(AemetClientException.MissingApiKey::class.java) {
            runTest { client(apiKey = "").fetchText("/anything") }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun missingDatosUrl_throwsAemetStatus() {
        server.enqueue(MockResponse().setBody("""{"estado":404,"descripcion":"No hay datos"}"""))

        val e = assertThrows(AemetClientException.AemetStatus::class.java) {
            runTest { client().fetchText("/prediccion/ccaa/hoy/mad") }
        }
        assertEquals(404, e.estado)
    }

    @Test
    fun nonRetryableHttpError_throwsHttp() {
        server.enqueue(MockResponse().setResponseCode(500))

        val e = assertThrows(AemetClientException.Http::class.java) {
            runTest { client().fetchText("/prediccion/ccaa/hoy/mad") }
        }
        assertEquals(500, e.code)
    }
}
