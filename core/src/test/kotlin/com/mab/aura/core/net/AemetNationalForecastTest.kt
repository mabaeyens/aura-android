package com.mab.aura.core.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Exercises the national text-forecast layer: the medium-range day-split parser ([AemetMedioPlazoParser]) as
 * pure text parsing, and the `hoy`-vs-`manana` today resolution ([nationalToday]) against a `MockWebServer`
 * (the same two-call envelope harness [AemetClientTest] uses). The `hoy`/`manana`/`pasadomanana` products
 * reuse [AemetBulletinParser], which its own test already covers.
 */
class AemetNationalForecastTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }

    private fun client() = AemetClient(
        apiKey = "KEY",
        baseUrl = server.url("/opendata/api").toString(),
        pacer = RequestPacer(limit = 1000),
        retryBackoffMillis = { 0 },
    )

    /** Enqueue one product's two-call pair: the datos envelope, then its plain-text payload. */
    private fun enqueueProduct(text: String) {
        server.enqueue(MockResponse().setBody("""{"estado":200,"datos":"${server.url("/datos")}"}"""))
        server.enqueue(MockResponse().setBody(text))
    }

    // --- Medium-range day split -----------------------------------------------------------------------

    private val medioPlazo = """
        AGENCIA ESTATAL DE METEOROLOGÍA
        PREDICCIÓN GENERAL DE MEDIO PLAZO PARA ESPAÑA
        DÍA 29 DE AGOSTO DE 2026 A LAS 14:21 HORA OFICIAL
        PREDICCIÓN VÁLIDA PARA LOS DÍAS 1 Y 2 DE SEPTIEMBRE DE 2026

        DÍA 01 (MARTES)
        Se mantendrá una situación de estabilidad en la mayor
        parte del país.

        Probables bancos de niebla matinales.

        DÍA 02 (MIÉRCOLES)
        Se prevé que se mantenga una situación dominada por
        las altas presiones.
    """.trimIndent()

    @Test
    fun medioPlazo_splitsIntoOneBlockPerDayHeader() {
        val outlook = AemetMedioPlazoParser.parse(medioPlazo)!!
        assertEquals(2, outlook.days.size)
        assertEquals(1, outlook.days[0].dia)
        assertEquals("MARTES", outlook.days[0].diaNombre)
        assertEquals(2, outlook.days[1].dia)
        assertEquals("MIÉRCOLES", outlook.days[1].diaNombre)
    }

    @Test
    fun medioPlazo_unfoldsWrappedParagraphsWithinADay() {
        val day = AemetMedioPlazoParser.parse(medioPlazo)!!.days[0]
        // The two hard-wrapped lines join into one paragraph; the blank line starts a second.
        assertEquals(
            "Se mantendrá una situación de estabilidad en la mayor parte del país.\n\n" +
                "Probables bancos de niebla matinales.",
            day.texto,
        )
    }

    @Test
    fun medioPlazo_keepsIssueDateAndVerbatimValidityLine() {
        val outlook = AemetMedioPlazoParser.parse(medioPlazo)!!
        val elaborado = outlook.elaborado!!.atZone(madrid)
        assertEquals(29, elaborado.dayOfMonth)
        assertEquals(8, elaborado.monthValue)
        assertEquals(14, elaborado.hour)
        assertEquals("PREDICCIÓN VÁLIDA PARA LOS DÍAS 1 Y 2 DE SEPTIEMBRE DE 2026", outlook.validez)
    }

    @Test
    fun medioPlazo_fallsBackToOneUnnamedBlockWhenNoDayHeaderMatches() {
        val noHeaders = """
            AGENCIA ESTATAL DE METEOROLOGÍA
            PREDICCIÓN GENERAL DE MEDIO PLAZO PARA ESPAÑA
            DÍA 29 DE AGOSTO DE 2026 A LAS 14:21 HORA OFICIAL
            PREDICCIÓN VÁLIDA PARA LOS PRÓXIMOS DÍAS

            Situación de estabilidad generalizada en todo el país.
        """.trimIndent()
        val outlook = AemetMedioPlazoParser.parse(noHeaders)!!
        assertEquals(1, outlook.days.size)
        assertEquals(0, outlook.days[0].dia)
        assertEquals("", outlook.days[0].diaNombre)
        assertEquals("Situación de estabilidad generalizada en todo el país.", outlook.days[0].texto)
    }

    // --- Today resolution: hoy when valid, else manana ------------------------------------------------

    private val hoyValidFor24 = """
        AGENCIA ESTATAL DE METEOROLOGÍA
        PREDICCIÓN GENERAL PARA ESPAÑA
        DÍA 24 DE AGOSTO DE 2026 A LAS 09:00 HORA OFICIAL
        PREDICCIÓN VÁLIDA PARA EL LUNES 24

        A.- FENÓMENOS SIGNIFICATIVOS
        Chubascos fuertes en el noreste.

        B.- PREDICCIÓN
        Jornada inestable en buena parte de la Península.
    """.trimIndent()

    private val mananaValidFor30 = """
        AGENCIA ESTATAL DE METEOROLOGÍA
        PREDICCIÓN GENERAL PARA ESPAÑA
        DÍA 29 DE AGOSTO DE 2026 A LAS 11:13 HORA OFICIAL
        PREDICCIÓN VÁLIDA PARA EL DOMINGO 30

        A.- FENÓMENOS SIGNIFICATIVOS
        No se esperan.

        B.- PREDICCIÓN
        Altas presiones con cielos poco nubosos.
    """.trimIndent()

    @Test
    fun nationalToday_usesHoyWhenItIsValidForToday() = runTest {
        enqueueProduct(hoyValidFor24)
        // "Today" is the 24th (08:00 UTC ≈ 10:00 Madrid), matching hoy's validity.
        val today = client().nationalToday(at = Instant.parse("2026-08-24T08:00:00Z"))!!
        assertEquals("Chubascos fuertes en el noreste.", today.bulletin.fenomenoSignificativo)
        assertTrue(today.bulletin.texto.startsWith("Jornada inestable"))
        // Only hoy was fetched: one product = the envelope + its payload.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun nationalToday_fallsBackToMananaWhenHoyIsStale() = runTest {
        enqueueProduct(hoyValidFor24)   // hoy still names the 24th …
        enqueueProduct(mananaValidFor30)
        // … but today is the 29th, so hoy is stale and the current manana stands in as today.
        val today = client().nationalToday(at = Instant.parse("2026-08-29T08:00:00Z"))!!
        assertTrue(today.bulletin.texto.startsWith("Altas presiones"))
        assertNull(today.bulletin.fenomenoSignificativo) // "No se esperan." → no phenomenon
        assertTrue(today.raw.contains("DOMINGO 30"))     // the raw kept is manana's, for caching
        assertEquals(4, server.requestCount)             // hoy then manana: two products
    }
}
