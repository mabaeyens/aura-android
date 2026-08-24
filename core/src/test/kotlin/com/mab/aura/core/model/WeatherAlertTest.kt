package com.mab.aura.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.Instant
import org.junit.Test

/**
 * Pins the ported `WeatherAlert` logic. The Swift `ActiveAlertTests` gate `WeatherSnapshot.activeAlert`,
 * which isn't ported yet; the behaviour it locks (amber-or-above and not-yet-expired) lives in
 * [WeatherAlert.isActive], so those cases are ported against that directly. The `shortLabel` classifier,
 * `provinceCode`, `topActive` and `AvisoArea` have no Swift test to port from.
 */
class WeatherAlertTest {

    private val base = Instant.ofEpochSecond(1_700_000_000)

    private fun alert(
        level: WeatherAlert.Level,
        expires: Instant?,
        event: String = "Aviso de lluvia",
        phenomenon: String? = "Lluvia",
        zona: String = "612801",
    ) = WeatherAlert(
        level = level, event = event, phenomenon = phenomenon,
        zona = zona, areaDesc = "Madrid", onset = null, expires = expires,
    )

    // --- isActive (ported from ActiveAlertTests) ---

    @Test
    fun isActive_amberNotYetExpired() {
        assertTrue(alert(WeatherAlert.Level.AMARILLO, base.plusSeconds(3600)).isActive(base))
    }

    @Test
    fun isActive_expiredIsInactive() {
        assertFalse(alert(WeatherAlert.Level.NARANJA, base.minusSeconds(1)).isActive(base))
    }

    @Test
    fun isActive_atExactExpiryInstantStillActive() {
        assertTrue(alert(WeatherAlert.Level.AMARILLO, base).isActive(base))
    }

    @Test
    fun isActive_greenNeverActive() {
        assertFalse(alert(WeatherAlert.Level.VERDE, base.plusSeconds(3600)).isActive(base))
    }

    @Test
    fun isActive_nilExpiryStaysActive() {
        assertTrue(alert(WeatherAlert.Level.ROJO, expires = null).isActive(base))
    }

    @Test
    fun levelRank_isLowToHigh() {
        assertEquals(0, WeatherAlert.Level.VERDE.rank)
        assertEquals(1, WeatherAlert.Level.AMARILLO.rank)
        assertEquals(2, WeatherAlert.Level.NARANJA.rank)
        assertEquals(3, WeatherAlert.Level.ROJO.rank)
    }

    // --- provinceCode ---

    @Test
    fun provinceCode_isDigitsThreeAndFour() {
        assertEquals("28", alert(WeatherAlert.Level.AMARILLO, null, zona = "612801").provinceCode)
        assertEquals("04", alert(WeatherAlert.Level.AMARILLO, null, zona = "610401").provinceCode)
        assertEquals("", alert(WeatherAlert.Level.AMARILLO, null, zona = "61").provinceCode)
    }

    // --- shortLabel classifier ---

    @Test
    fun shortLabel_mapsPhenomenaToPlainWords() {
        fun label(event: String, phenomenon: String?) =
            alert(WeatherAlert.Level.AMARILLO, null, event = event, phenomenon = phenomenon).shortLabel
        assertEquals("Costa", label("Aviso costero", "Fenómenos costeros"))
        assertEquals("Tormentas", label("Aviso", "Tormentas"))
        assertEquals("Nieve", label("Aviso", "Nevadas"))
        assertEquals("Lluvia", label("Aviso", "Lluvia"))
        assertEquals("Viento", label("Aviso", "Rachas máximas de viento"))
        assertEquals("Calor", label("Aviso de temperaturas máximas", "Temperatura máxima"))
        assertEquals("Frío", label("Aviso", "Heladas"))
    }

    @Test
    fun shortLabel_weatherPhenomenaWinOverTemperature() {
        // "rachas máximas de viento" contains "máxim" (Calor) but "viento"/"racha" is checked first.
        val label = alert(
            WeatherAlert.Level.NARANJA, null,
            event = "Aviso", phenomenon = "Rachas máximas de viento",
        ).shortLabel
        assertEquals("Viento", label)
    }

    @Test
    fun shortLabel_unknownFallsBackToFirstWordThenAviso() {
        assertEquals(
            "Granizo",
            alert(WeatherAlert.Level.AMARILLO, null, event = "Aviso", phenomenon = "GRANIZO fuerte").shortLabel,
        )
        assertEquals(
            "Aviso",
            alert(WeatherAlert.Level.AMARILLO, null, event = "Algo", phenomenon = null).shortLabel,
        )
    }

    // --- topActive ---

    @Test
    fun topActive_picksMostSevereActiveForProvince() {
        val alerts = listOf(
            alert(WeatherAlert.Level.AMARILLO, base.plusSeconds(3600), zona = "612801"), // prov 28, active
            alert(WeatherAlert.Level.ROJO, base.plusSeconds(3600), zona = "612801"),      // prov 28, active, top
            alert(WeatherAlert.Level.ROJO, base.plusSeconds(3600), zona = "610401"),      // prov 04, other province
            alert(WeatherAlert.Level.NARANJA, base.minusSeconds(1), zona = "612801"),     // prov 28 but expired
        )
        val top = alerts.topActive(forProvince = "28", now = base)
        assertEquals(WeatherAlert.Level.ROJO, top?.level)
        assertEquals("28", top?.provinceCode)
    }

    @Test
    fun topActive_nullWhenNoneActiveForProvince() {
        val alerts = listOf(alert(WeatherAlert.Level.VERDE, base.plusSeconds(3600), zona = "612801"))
        assertNull(alerts.topActive(forProvince = "28", now = base))
    }

    // --- AvisoArea ---

    @Test
    fun avisoArea_mapsProvinceToBulletinArea() {
        assertEquals("72", AvisoArea.forProvincia("28")) // Madrid
        assertEquals("64", AvisoArea.forProvincia("07")) // Balears, island override
        assertNull(AvisoArea.forProvincia("99"))
    }
}
