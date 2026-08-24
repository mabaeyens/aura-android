package com.mab.aura.core.net

import com.mab.aura.core.model.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pins the CAP-XML parsing ported from `CAPParser` in `WeatherAlert.swift`. The Swift side has no unit
 * test for the parser itself (only the `WeatherAlert` model rules), so these pin the ported behaviour
 * directly against a synthetic-but-realistic AEMET meteoalerta CAP document: one alert per warning zone,
 * Spanish info only, level and phenomenon extraction, and offset timestamps.
 */
class CAPParserTest {

    // A Spanish info with two warning zones (→ two alerts) and an English info that must be ignored.
    private val capXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
          <identifier>2.49.0.1.724.0.example</identifier>
          <info>
            <language>es-ES</language>
            <event>Lluvias</event>
            <onset>2026-08-21T10:00:00+02:00</onset>
            <expires>2026-08-21T20:00:00+02:00</expires>
            <parameter><valueName>AEMET-Meteoalerta nivel</valueName><value>amarillo</value></parameter>
            <parameter><valueName>AEMET-Meteoalerta parametro</valueName><value>PR;Lluvias;40 mm en 1 hora</value></parameter>
            <area>
              <areaDesc>Madrid capital</areaDesc>
              <geocode><valueName>AEMET-Meteoalerta zona</valueName><value>772201</value></geocode>
            </area>
            <area>
              <areaDesc>Sierra de Madrid</areaDesc>
              <geocode><valueName>AEMET-Meteoalerta zona</valueName><value>772202</value></geocode>
            </area>
          </info>
          <info>
            <language>en-GB</language>
            <event>Rain</event>
            <parameter><valueName>AEMET-Meteoalerta nivel</valueName><value>yellow</value></parameter>
            <area>
              <areaDesc>Madrid city</areaDesc>
              <geocode><valueName>AEMET-Meteoalerta zona</valueName><value>772201</value></geocode>
            </area>
          </info>
        </alert>
    """.trimIndent()

    @Test
    fun parse_oneAlertPerZone_spanishInfoOnly() {
        val alerts = CAPParser.parse(capXml.toByteArray())
        // Two zones in the Spanish info; the English info is dropped entirely.
        assertEquals(2, alerts.size)
        assertTrue(alerts.all { it.level == WeatherAlert.Level.AMARILLO })
        assertTrue(alerts.all { it.event == "Lluvias" })
        assertTrue(alerts.all { it.phenomenon == "Lluvias" })   // second ";"-field of the parametro
        assertEquals(setOf("772201", "772202"), alerts.map { it.zona }.toSet())
        assertEquals("Madrid capital", alerts.first { it.zona == "772201" }.areaDesc)
        assertEquals("Sierra de Madrid", alerts.first { it.zona == "772202" }.areaDesc)
    }

    @Test
    fun parse_readsOffsetTimestamps() {
        val alert = CAPParser.parse(capXml.toByteArray()).first()
        // +02:00 local resolves to UTC.
        assertEquals(Instant.parse("2026-08-21T08:00:00Z"), alert.onset)
        assertEquals(Instant.parse("2026-08-21T18:00:00Z"), alert.expires)
    }

    @Test
    fun parse_greenAndUnknownLevelsAreDropped() {
        val green = """
            <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
              <info>
                <language>es-ES</language>
                <event>Sin avisos</event>
                <parameter><valueName>AEMET-Meteoalerta nivel</valueName><value>verde</value></parameter>
                <area><areaDesc>Madrid</areaDesc>
                  <geocode><valueName>AEMET-Meteoalerta zona</valueName><value>772201</value></geocode>
                </area>
              </info>
            </alert>
        """.trimIndent()
        // A "verde" info still produces WeatherAlert(level = VERDE) — CAPParser keeps it (VERDE is a valid
        // Level); it's `isActive`/`topActive` that never surface a green. So it parses but is not active.
        val alerts = CAPParser.parse(green.toByteArray())
        assertEquals(1, alerts.size)
        assertEquals(WeatherAlert.Level.VERDE, alerts.first().level)
        assertTrue(alerts.none { it.isActive(Instant.parse("2026-08-21T12:00:00Z")) })
    }

    @Test
    fun parse_missingLevelYieldsNoAlert() {
        val noLevel = """
            <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
              <info>
                <language>es-ES</language>
                <event>Lluvias</event>
                <area><areaDesc>Madrid</areaDesc>
                  <geocode><valueName>AEMET-Meteoalerta zona</valueName><value>772201</value></geocode>
                </area>
              </info>
            </alert>
        """.trimIndent()
        assertTrue(CAPParser.parse(noLevel.toByteArray()).isEmpty())
    }

    @Test
    fun parse_malformedXmlYieldsEmpty() {
        assertTrue(CAPParser.parse("not xml at all".toByteArray()).isEmpty())
        assertTrue(CAPParser.parse(ByteArray(0)).isEmpty())
    }

    @Test
    fun parse_phenomenonNullWhenParametroHasOneField() {
        val single = """
            <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
              <info>
                <language>es-ES</language>
                <event>Viento</event>
                <parameter><valueName>AEMET-Meteoalerta nivel</valueName><value>naranja</value></parameter>
                <parameter><valueName>AEMET-Meteoalerta parametro</valueName><value>VI</value></parameter>
                <area><areaDesc>Madrid</areaDesc>
                  <geocode><valueName>AEMET-Meteoalerta zona</valueName><value>772201</value></geocode>
                </area>
              </info>
            </alert>
        """.trimIndent()
        val alert = CAPParser.parse(single.toByteArray()).single()
        assertEquals(WeatherAlert.Level.NARANJA, alert.level)
        assertNull(alert.phenomenon)
    }
}
