package com.mab.aura.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Exercises the ported `AemetBulletinParser` against AEMET's fixed-layout `ascii/txt` community bulletin:
 * the section split (A.- / B.-), the hard-wrap unfolding into paragraphs, the "no significant phenomena"
 * detection, and the issue/validity date parsing in Europe/Madrid civil time.
 */
class AemetBulletinParserTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    // A representative bulletin: two hard-wrapped paragraphs in section B, a real phenomenon in section A,
    // issued the 18th and valid for the 19th.
    private val sample = """
        AGENCIA ESTATAL DE METEOROLOGÍA
        PREDICCIÓN GENERAL PARA LA COMUNIDAD DE MADRID
        DÍA 18 DE AGOSTO DE 2026 A LAS 12:38 HORA OFICIAL
        PREDICCIÓN VÁLIDA PARA EL MIÉRCOLES 19

        A.- FENÓMENOS SIGNIFICATIVOS
        Tormentas localmente fuertes por la tarde en la sierra.

        B.- PREDICCIÓN
        Cielo poco nuboso a primeras horas, aumentando a nuboso
        por la tarde con probables chubascos y tormentas.

        Temperaturas en ligero descenso. Viento flojo del oeste.
    """.trimIndent()

    @Test
    fun parse_unfoldsWrappedParagraphsInSectionB() {
        val bulletin = AemetBulletinParser.parse(sample)
        assertNotNull(bulletin)
        // Two paragraphs, each with its own hard wraps joined by spaces, separated by a blank line.
        assertEquals(
            "Cielo poco nuboso a primeras horas, aumentando a nuboso por la tarde con probables chubascos y tormentas.\n\n" +
                "Temperaturas en ligero descenso. Viento flojo del oeste.",
            bulletin!!.texto,
        )
    }

    @Test
    fun parse_readsTheSignificantPhenomenon() {
        val bulletin = AemetBulletinParser.parse(sample)!!
        assertEquals("Tormentas localmente fuertes por la tarde en la sierra.", bulletin.fenomenoSignificativo)
    }

    @Test
    fun parse_readsIssueAndValidityDatesInMadridTime() {
        val bulletin = AemetBulletinParser.parse(sample)!!
        val elaborado = bulletin.elaborado!!.atZone(madrid)
        assertEquals(2026, elaborado.year)
        assertEquals(8, elaborado.monthValue)
        assertEquals(18, elaborado.dayOfMonth)
        assertEquals(12, elaborado.hour)
        assertEquals(38, elaborado.minute)

        val validez = bulletin.validezInicio!!.atZone(madrid)
        assertEquals(19, validez.dayOfMonth)
        assertEquals(8, validez.monthValue)
        assertEquals(2026, validez.year)
    }

    @Test
    fun parse_treatsNoSignificantPhenomenaAsNull() {
        val quiet = sample.replace(
            "Tormentas localmente fuertes por la tarde en la sierra.",
            "No se esperan fenómenos significativos.",
        )
        val bulletin = AemetBulletinParser.parse(quiet)!!
        assertNull(bulletin.fenomenoSignificativo)
    }

    @Test
    fun parse_rollsValidityIntoNextMonthWhenDayPrecedesIssueDay() {
        // Issued on the 31st, valid for the 1st — the validity date must roll into the next month.
        val crossing = sample
            .replace("DÍA 18 DE AGOSTO DE 2026", "DÍA 31 DE AGOSTO DE 2026")
            .replace("PREDICCIÓN VÁLIDA PARA EL MIÉRCOLES 19", "PREDICCIÓN VÁLIDA PARA EL LUNES 1")
        val validez = AemetBulletinParser.parse(crossing)!!.validezInicio!!.atZone(madrid)
        assertEquals(1, validez.dayOfMonth)
        assertEquals(9, validez.monthValue)
        assertEquals(2026, validez.year)
    }

    @Test
    fun parse_returnsNullWhenSectionBIsMissing() {
        val noBody = """
            AGENCIA ESTATAL DE METEOROLOGÍA
            DÍA 18 DE AGOSTO DE 2026 A LAS 12:38 HORA OFICIAL

            A.- FENÓMENOS SIGNIFICATIVOS
            No se esperan.
        """.trimIndent()
        assertNull(AemetBulletinParser.parse(noBody))
    }

    @Test
    fun parse_toleratesCrlfLineEndings() {
        val crlf = sample.replace("\n", "\r\n")
        val bulletin = AemetBulletinParser.parse(crlf)
        assertNotNull(bulletin)
        assertTrue(bulletin!!.texto.startsWith("Cielo poco nuboso"))
    }
}
