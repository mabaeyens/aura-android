package com.mab.aura.core.wind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Parity port of `WindDirectionTests.swift`, plus the AEMET-token cases the Swift suite implied. */
class WindDirectionTest {

    @Test
    fun abbreviations() {
        assertEquals("N", WindDirection.N.abbreviation)
        assertEquals("ONO", WindDirection.ONO.abbreviation)
        assertEquals("SSE", WindDirection.SSE.abbreviation)
        assertEquals("O", WindDirection.O.abbreviation)
    }

    @Test
    fun spanishNames() {
        assertEquals("Oesnoroeste", WindDirection.ONO.spanishName)
        assertEquals("Nornordeste", WindDirection.NNE.spanishName)
    }

    @Test
    fun fromDegrees() {
        assertEquals(WindDirection.N, WindDirection.fromDegrees(0.0))
        assertEquals(WindDirection.E, WindDirection.fromDegrees(90.0))
        assertEquals(WindDirection.S, WindDirection.fromDegrees(180.0))
        assertEquals(WindDirection.O, WindDirection.fromDegrees(270.0))
        assertEquals(WindDirection.ONO, WindDirection.fromDegrees(292.5))
        assertEquals(WindDirection.N, WindDirection.fromDegrees(360.0))
        assertEquals(WindDirection.NNO, WindDirection.fromDegrees(-22.5))
        assertEquals(WindDirection.N, WindDirection.fromDegrees(11.0))    // rounds down to N sector
        assertEquals(WindDirection.NNE, WindDirection.fromDegrees(12.0))  // rounds up to NNE
    }

    @Test
    fun allSixteenPresent() {
        assertEquals(16, WindDirection.entries.size)
        assertEquals(16, WindDirection.entries.map { it.abbreviation }.toSet().size)
    }

    @Test
    fun fromAemet_matchesTokens_andRejectsCalmAndJunk() {
        assertEquals(WindDirection.NO, WindDirection.fromAemet("NO"))
        assertEquals(WindDirection.ONO, WindDirection.fromAemet(" ono "))
        assertNull(WindDirection.fromAemet("C"))
        assertNull(WindDirection.fromAemet(""))
    }
}
