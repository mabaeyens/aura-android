package com.mab.aura.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Decode tests for the AEMET wire models. These pin down the two behaviours that differ from Swift's
 * `Codable` and are easy to get wrong: a missing key must decode as `null` (which only works because
 * every optional carries a `= null` default), and the client's `Json` must ignore the many fields
 * these subset models don't declare. The [json] instance here mirrors what `AEMETClient` will use.
 */
class AemetModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun uviForecast_mapsShoutyHeaderKeys_andToleratesMissingValor() {
        val payload = """
            {
              "FECHA_VALIDEZ": "2026-08-24",
              "CIUDAD": [
                { "id": "28079", "valor": "Madrid", "uv": "8" },
                { "id": "08019", "uv": "6" }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString<UVIForecast>(payload)

        assertEquals("2026-08-24", decoded.fechaValidez)
        assertEquals(2, decoded.ciudad.size)
        assertEquals("28079", decoded.ciudad[0].id)
        assertEquals("Madrid", decoded.ciudad[0].valor)
        assertEquals("8", decoded.ciudad[0].uv)
        // valor omitted on the second city: decodes as null, not a decode failure.
        assertNull(decoded.ciudad[1].valor)
        assertEquals("6", decoded.ciudad[1].uv)
    }

    @Test
    fun municipioForecast_dayWithOnlyFecha_leavesEveryOptionalNull() {
        // Days 4–6 of the daily feed can carry almost nothing but the date. Every optional block must
        // decode as null rather than throwing MissingFieldException.
        val payload = """
            {
              "nombre": "Madrid",
              "provincia": "Madrid",
              "prediccion": { "dia": [ { "fecha": "2026-08-28" } ] }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<MunicipioForecast>(payload)
        val day = decoded.prediccion.dia.single()

        assertEquals("2026-08-28", day.fecha)
        assertNull(day.temperatura)
        assertNull(day.humedadRelativa)
        assertNull(day.estadoCielo)
        assertNull(day.viento)
        assertNull(day.probPrecipitacion)
    }

    @Test
    fun municipioForecast_ignoresUnknownFields() {
        // AEMET sends more than this subset declares (e.g. `elaborado`, `id`); the configured Json
        // must skip them instead of failing.
        val payload = """
            {
              "nombre": "Madrid",
              "provincia": "Madrid",
              "elaborado": "2026-08-24T09:00:00",
              "prediccion": {
                "dia": [
                  {
                    "fecha": "2026-08-24",
                    "temperatura": { "maxima": 33, "minima": 19 },
                    "probPrecipitacion": [ { "value": 5, "periodo": "00-24" } ],
                    "orto": "07:24"
                  }
                ]
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<MunicipioForecast>(payload)
        val day = decoded.prediccion.dia.single()

        assertEquals(33, day.temperatura?.maxima)
        assertEquals(19, day.temperatura?.minima)
        assertEquals(5, day.probPrecipitacion?.single()?.value)
        assertEquals("00-24", day.probPrecipitacion?.single()?.periodo)
    }

    @Test
    fun municipioHourly_decodesParallelArrays_andOptionalWind() {
        val payload = """
            {
              "nombre": "Madrid",
              "provincia": "Madrid",
              "prediccion": {
                "dia": [
                  {
                    "fecha": "2026-08-24",
                    "temperatura": [ { "value": "24", "periodo": "10" } ],
                    "estadoCielo": [ { "value": "11", "periodo": "10", "descripcion": "Despejado" } ],
                    "humedadRelativa": [ { "value": "40", "periodo": "10" } ],
                    "probPrecipitacion": [ { "value": "0", "periodo": "0814" } ],
                    "vientoAndRachaMax": [
                      { "periodo": "10", "direccion": [ "NE" ], "velocidad": [ "12" ] },
                      { "periodo": "10", "value": "25" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<MunicipioHourly>(payload)
        val day = decoded.prediccion.dia.single()

        assertEquals("24", day.temperatura.single().value)
        assertEquals("Despejado", day.estadoCielo.single().descripcion)
        // Absent optional hourly arrays stay null.
        assertNull(day.sensTermica)
        assertNull(day.precipitacion)
        // Mixed wind entries: a direction+speed reading and a scalar gust.
        val wind = day.vientoAndRachaMax!!
        assertEquals("NE", wind[0].direccion?.single())
        assertEquals("12", wind[0].velocidad?.single())
        assertEquals("25", wind[1].value)
        assertNull(wind[1].direccion)
    }
}
