package com.mab.aura.core.uv

import com.mab.aura.core.model.UVIForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Band-boundary tests for the ported `UVIndex` value logic (no Swift `UVIndexTests` exists to port
 * from, so these pin the WHO band edges directly), plus [UVIndex.pick] against the wire model.
 */
class UVIndexTest {

    @Test
    fun bandName_atEveryBoundary() {
        assertEquals("Bajo", UVIndex(0).bandName)
        assertEquals("Bajo", UVIndex(2).bandName)
        assertEquals("Moderado", UVIndex(3).bandName)
        assertEquals("Moderado", UVIndex(5).bandName)
        assertEquals("Alto", UVIndex(6).bandName)
        assertEquals("Alto", UVIndex(7).bandName)
        assertEquals("Muy alto", UVIndex(8).bandName)
        assertEquals("Muy alto", UVIndex(10).bandName)
        assertEquals("Extremadamente alto", UVIndex(11).bandName)
        assertEquals("Extremadamente alto", UVIndex(15).bandName)
    }

    @Test
    fun advice_tracksTheBands() {
        assertEquals("Sin protección necesaria", UVIndex(0).advice)
        assertEquals("Gafas de sol y crema", UVIndex(4).advice)
        assertEquals("Protección recomendada", UVIndex(6).advice)
        assertEquals("Evita el sol del mediodía", UVIndex(9).advice)
        assertEquals("Evita la exposición al sol", UVIndex(11).advice)
    }

    @Test
    fun pick_exactIneMatch_orNull() {
        val cities = listOf(
            UVIForecast.City(id = "28079", valor = "Madrid", uv = "8"),
            UVIForecast.City(id = "08019", valor = "Barcelona", uv = "6"),
        )
        assertEquals(UVIndex(8), UVIndex.pick("28079", cities))
        assertEquals(UVIndex(6), UVIndex.pick("08019", cities))
        assertNull(UVIndex.pick("99999", cities)) // not listed
    }

    @Test
    fun pick_nonNumericUv_isNull() {
        val cities = listOf(UVIForecast.City(id = "28079", valor = "Madrid", uv = "n/d"))
        assertNull(UVIndex.pick("28079", cities))
    }
}
