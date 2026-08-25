package com.mab.aura.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported behaviour of `BulletinText.sentences`: AEMET's run-on paragraph, with hard mid-sentence wraps,
 * comes back as one trimmed line per sentence with no empty lines.
 */
class BulletinTextTest {

    @Test
    fun `collapses AEMET mid-sentence hard wraps into a single flowed line`() {
        // A newline (with a leading space, as AEMET emits) baked into the middle of one sentence.
        val input = "Cielo poco nuboso con intervalos de nubes altas por la\n tarde"
        assertEquals(listOf("Cielo poco nuboso con intervalos de nubes altas por la tarde"),
            BulletinText.sentences(input))
    }

    @Test
    fun `splits one line per sentence on the full stop boundary`() {
        val input = "Cielo despejado. Temperaturas en ascenso. Viento flojo del norte."
        assertEquals(
            listOf(
                "Cielo despejado.",
                "Temperaturas en ascenso.",
                "Viento flojo del norte.",
            ),
            BulletinText.sentences(input),
        )
    }

    @Test
    fun `keeps decimals written with a comma inside one sentence`() {
        // AEMET writes decimals with a comma, so "." only ever ends a sentence, never a number.
        val input = "Cotas de nieve en torno a 1.200 m. Máximas de 12,5 grados."
        val out = BulletinText.sentences(input)
        assertEquals(2, out.size)
        assertTrue(out[1].contains("12,5"))
    }

    @Test
    fun `drops empty and whitespace-only fragments`() {
        val input = "   \n  Cielo nuboso.\n\n  "
        assertEquals(listOf("Cielo nuboso."), BulletinText.sentences(input))
    }
}
