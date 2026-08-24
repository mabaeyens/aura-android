package com.mab.aura.core.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ports the MITECO-client cases from the Swift `AirQualityTests` (CSV parse + nearest) and
 * `AirComponentsTests` (the `sql1` breakdown rules + requestBody), plus a MockWebServer check of the
 * network `stations()` fetch. The `valueText`/`label` model cases already live in `AirQualityTest`.
 */
class MitecoAirQualityTest {

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

    // A slice of the real ica-ultima-hora.csv: header + active/inactive rows, a full-data and a partial
    // (×10) index, a no-data (0) row, and an inactive row.
    private val csv = """
        cod_estacion,nombre,tipo,latitud,longitud,activa,fecha,indice,debido_a
        28079035,PLAZA DEL CARMEN,FONDO,40.41932,-3.70237,true,2026-08-21T08:00:00,20,O3
        28079004,RETIRO,FONDO,40.41467,-3.68258,true,2026-08-21T08:00:00,2,O3
        28079008,ESCUELAS AGUIRRE,TRAFICO,40.42167,-3.68232,true,2026-08-21T08:00:00,0,NO2
        08019004,BARCELONA EIXAMPLE,TRAFICO,41.38539,2.15382,true,2026-08-21T08:00:00,3,PM10
        99999999,APAGADA,FONDO,40.0,-3.0,false,2026-08-21T08:00:00,1,O3
    """.trimIndent()

    // --- CSV parse (from AirQualityTests) ---

    @Test
    fun parse_keepsOnlyActiveStationsWithData() {
        val stations = MitecoAirQuality.parse(csv)
        // Drops the inactive station and the índice-0 (no-data) row.
        assertEquals(3, stations.size)
        assertFalse(stations.any { it.name == "APAGADA" })
        assertFalse(stations.any { it.name == "ESCUELAS AGUIRRE" })
    }

    @Test
    fun nearest_picksClosestAndDecodesFullCategory() {
        val stations = MitecoAirQuality.parse(csv)
        // A point right by El Retiro.
        val aq = MitecoAirQuality.nearest(latitude = 40.4145, longitude = -3.6830, stations = stations)
        assertEquals("Retiro", aq?.station)           // title-cased from RETIRO
        assertEquals(2, aq?.category)
        assertEquals(false, aq?.partial)
        assertEquals("O3", aq?.pollutant)
        assertEquals("Razonablemente buena", aq?.categoryName)
        assertEquals("O₃", aq?.pollutantLabel)
        assertTrue((aq?.distanceKm ?: Double.POSITIVE_INFINITY) < 1)
    }

    @Test
    fun nearest_partialIndexIsCategoryTimesTen() {
        val stations = MitecoAirQuality.parse(csv)
        // A point right by Plaza del Carmen (índice 20 = category 2, partial).
        val aq = MitecoAirQuality.nearest(latitude = 40.4193, longitude = -3.7024, stations = stations)
        assertEquals("Plaza Del Carmen", aq?.station)
        assertEquals(2, aq?.category)
        assertEquals(true, aq?.partial)
    }

    @Test
    fun nearest_isNullWithNoStations() {
        assertNull(MitecoAirQuality.nearest(latitude = 40.0, longitude = -3.0, stations = emptyList()))
    }

    // --- Backend breakdown parse (from AirComponentsTests) ---

    @Test
    fun parseComponents_onlyMeasuredPollutantsSurvive() {
        // A traffic station: only NO₂ is measured; every other pollutant is null/unvalidated all day.
        val json = """
            [{"hora":8,"magnitud":"NO2","valor_medido":22,"dato_medido":true,"valor_media_movil":null,"dato_medido_mm":false},
             {"hora":9,"magnitud":"NO2","valor_medido":27,"dato_medido":true,"valor_media_movil":null,"dato_medido_mm":false},
             {"hora":9,"magnitud":"O3","valor_medido":null,"dato_medido":false,"valor_media_movil":null,"dato_medido_mm":false},
             {"hora":9,"magnitud":"PM10","valor_medido":null,"dato_medido":false,"valor_media_movil":null,"dato_medido_mm":false},
             {"hora":9,"magnitud":"SO2","valor_medido":null,"dato_medido":false,"valor_media_movil":null,"dato_medido_mm":false}]
        """.trimIndent()
        val comps = MitecoAirQuality.parseComponents(json)
        assertEquals(listOf("NO2"), comps.map { it.pollutant })
        assertEquals(27.0, comps.first().value, 1e-9)   // the latest valid hour (9), not an earlier one
    }

    @Test
    fun parseComponents_fullBreakdownLatestHourAndOrder() {
        val json = """
            [{"hora":9,"magnitud":"SO2","valor_medido":4,"dato_medido":true,"valor_media_movil":null,"dato_medido_mm":false},
             {"hora":9,"magnitud":"PM10","valor_medido":3,"dato_medido":true,"valor_media_movil":3.5,"dato_medido_mm":true},
             {"hora":9,"magnitud":"PM2.5","valor_medido":1,"dato_medido":true,"valor_media_movil":null,"dato_medido_mm":false},
             {"hora":9,"magnitud":"O3","valor_medido":60,"dato_medido":true,"valor_media_movil":63,"dato_medido_mm":true},
             {"hora":8,"magnitud":"NO2","valor_medido":9,"dato_medido":true,"valor_media_movil":null,"dato_medido_mm":false},
             {"hora":9,"magnitud":"NO2","valor_medido":3,"dato_medido":true,"valor_media_movil":null,"dato_medido_mm":false}]
        """.trimIndent()
        // AirQuality.create applies the canonical NO₂, O₃, PM2.5, PM10, SO₂ order (Swift does it in init).
        val aq = com.mab.aura.core.air.AirQuality.create(
            category = 1, partial = false, pollutant = "O3",
            station = "Valderejo", distanceKm = 5.0, measured = java.time.Instant.now(),
            components = MitecoAirQuality.parseComponents(json),
        )
        assertEquals(listOf("NO2", "O3", "PM2.5", "PM10", "SO2"), aq.components.map { it.pollutant })
        // NO₂ has no moving average, so the latest valid hour (3, not hour 8's 9) is used.
        assertEquals(3.0, aq.components.first { it.pollutant == "NO2" }.value, 1e-9)
        // O₃ uses its 8 h running mean (63), the value the ICA is built from.
        assertEquals(63.0, aq.components.first { it.pollutant == "O3" }.value, 1e-9)
        // PM10 likewise uses its 24 h running mean (3.5), not the raw hourly 3.
        assertEquals(3.5, aq.components.first { it.pollutant == "PM10" }.value, 1e-9)
    }

    @Test
    fun parseComponents_invalidHourDoesNotShadowValid() {
        val json = """
            [{"hora":8,"magnitud":"NO2","valor_medido":22,"dato_medido":true,"valor_media_movil":null,"dato_medido_mm":false},
             {"hora":9,"magnitud":"NO2","valor_medido":99,"dato_medido":false,"valor_media_movil":null,"dato_medido_mm":false}]
        """.trimIndent()
        val comps = MitecoAirQuality.parseComponents(json)
        assertEquals(listOf("NO2"), comps.map { it.pollutant })
        assertEquals(22.0, comps.first().value, 1e-9)   // hour-9 row is unvalidated and skipped
    }

    @Test
    fun parseComponents_emptyAndMalformed() {
        assertTrue(MitecoAirQuality.parseComponents("[]").isEmpty())
        assertTrue(MitecoAirQuality.parseComponents("not json").isEmpty())
        // The backend's error string is not JSON.
        assertTrue(MitecoAirQuality.parseComponents("Consulta incorrecta").isEmpty())
    }

    @Test
    fun requestBody_keepsLiteralSeparator() {
        val s = String(MitecoAirQuality.requestBody(code = 1055001, day = "20260821"), Charsets.UTF_8)
        assertTrue("the 'sql=' separator stays literal", s.startsWith("sql=sql1"))
        assertFalse("the '=' must never be percent-encoded", s.contains("%3D"))
        assertFalse("the '#' delimiters must be escaped to %23", s.contains("#"))
        assertFalse("the space must be escaped to %20", s.contains(" "))
        assertTrue("station code sits between escaped '#' delimiters", s.contains("%231055001%23"))
    }

    // --- composite driver rule (no Swift test; pins the ported comparator) ---

    @Test
    fun composite_worstBandDrivesAndTiesGoToNearer() {
        val no2 = com.mab.aura.core.air.AirComponent(
            pollutant = "NO2", value = 100.0, station = "Far", distanceKm = 20.0,
        ) // NO2 band 3 (90–120)
        val o3 = com.mab.aura.core.air.AirComponent(
            pollutant = "O3", value = 45.0, station = "Near", distanceKm = 2.0,
        ) // O3 band 1 (<=50)
        val pm10 = com.mab.aura.core.air.AirComponent(
            pollutant = "PM10", value = 45.0, station = "Nearest", distanceKm = 1.0,
        ) // PM10 band 3 (40–50), same band as NO2 but nearer
        val aq = MitecoAirQuality.composite(listOf(no2, o3, pm10))!!
        // NO2 and PM10 both sit in band 3; the nearer (PM10, 1 km) wins the tie and drives the headline.
        assertEquals(3, aq.category)
        assertEquals("PM10", aq.pollutant)
        assertEquals("Nearest", aq.station)
    }

    @Test
    fun composite_isNullForEmptyBreakdown() {
        assertNull(MitecoAirQuality.composite(emptyList()))
    }

    // --- stations() network fetch ---

    @Test
    fun stations_fetchesAndParsesTheFeed() = runTest {
        server.enqueue(MockResponse().setBody(csv))
        val client = MitecoAirQuality(feedUrl = server.url("/datos/ica-ultima-hora.csv").toString())
        assertEquals(3, client.stations().size)
    }

    @Test
    fun stations_emptyOnNon200() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val client = MitecoAirQuality(feedUrl = server.url("/datos/ica-ultima-hora.csv").toString())
        assertTrue(client.stations().isEmpty())
    }
}
