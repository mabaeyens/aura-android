package com.mab.aura.core.geo

import com.mab.aura.core.model.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ComunidadTest {

    @Test
    fun knownProvincesResolveToTheirCommunity() {
        assertEquals("mad", Comunidad.forProvincia("28")?.code)
        assertEquals("Comunidad de Madrid", Comunidad.forProvincia("28")?.nombre)
        assertEquals("cat", Comunidad.forProvincia("08")?.code)
        assertEquals("Canarias", Comunidad.forProvincia("35")?.nombre)
        assertEquals("Ciudad de Ceuta", Comunidad.forProvincia("51")?.nombre)
        assertEquals("Ciudad de Melilla", Comunidad.forProvincia("52")?.nombre)
    }

    @Test
    fun unknownProvinceIsNull() {
        assertNull(Comunidad.forProvincia("99"))
        assertNull(Comunidad.forProvincia(""))
    }

    @Test
    fun allFiftyTwoProvincesMap() {
        for (n in 1..52) {
            val code = n.toString().padStart(2, '0')
            assertNotNull("province $code should resolve", Comunidad.forProvincia(code))
        }
    }

    @Test
    fun locationExtensionUsesTheProvinceCode() {
        val madrid = Location(ine = "28079", nombre = "Madrid", provincia = "Madrid", latitude = 40.4, longitude = -3.7)
        assertEquals("mad", madrid.comunidad?.code)
        // A Barcelona INE code (08...) maps to Cataluña.
        val barcelona = Location(ine = "08019", nombre = "Barcelona", provincia = "Barcelona", latitude = 41.4, longitude = 2.2)
        assertEquals("Cataluña", barcelona.comunidad?.nombre)
    }
}
