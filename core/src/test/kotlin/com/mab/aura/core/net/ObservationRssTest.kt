package com.mab.aura.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Pins [ObservationRss.latestUpdate], which reads AEMET's keyless observation RSS notifier for the newest
 * "Última actualización" publish time. The sample mirrors the live feed's shape (see the unified-freshness
 * design): each `<item>`'s `<description>` is a small JSON blob carrying the ISO timestamp with a `+0200`
 * offset, and the channel wraps them newest-first.
 */
class ObservationRssTest {

    private fun feed(vararg descriptions: String): ByteArray {
        val items = descriptions.joinToString("") { desc ->
            "<item><title>Actualización</title><description>$desc</description>" +
                "<pubDate>Fri, 28 Aug 2026 11:31:59 +0200</pubDate></item>"
        }
        return ("""<?xml version="1.0" encoding="UTF-8"?>""" +
            """<rss version="2.0"><channel><title>Observación convencional</title>$items</channel></rss>""")
            .toByteArray(Charsets.UTF_8)
    }

    private fun update(iso: String) = """{"Última actualización": "$iso"}"""

    @Test
    fun latestUpdate_parsesTheIsoOffsetTimestamp() {
        val data = feed(update("2026-08-28T11:31:59+0200"))
        // 11:31:59 +0200 == 09:31:59 UTC.
        assertEquals(Instant.parse("2026-08-28T09:31:59Z"), ObservationRss.latestUpdate(data))
    }

    @Test
    fun latestUpdate_takesTheMaxRegardlessOfItemOrder() {
        // Items deliberately out of newest-first order: the max must win, not the first.
        val data = feed(
            update("2026-08-28T02:32:25+0200"),
            update("2026-08-28T11:31:59+0200"),
            update("2026-08-28T10:31:54+0200"),
        )
        assertEquals(Instant.parse("2026-08-28T09:31:59Z"), ObservationRss.latestUpdate(data))
    }

    @Test
    fun latestUpdate_skipsUnparseableItemsButKeepsGoodOnes() {
        val data = feed(
            """{"Última actualización": "not a date"}""",
            update("2026-08-28T10:31:54+0200"),
        )
        assertEquals(Instant.parse("2026-08-28T08:31:54Z"), ObservationRss.latestUpdate(data))
    }

    @Test
    fun latestUpdate_nullWhenNoItemCarriesATimestamp() {
        assertNull(ObservationRss.latestUpdate(feed("""{"Última actualización": ""}""")))
        assertNull(ObservationRss.latestUpdate(feed("no timestamp here")))
    }

    @Test
    fun latestUpdate_nullOnEmptyOrMalformedXml() {
        assertNull(ObservationRss.latestUpdate(ByteArray(0)))
        assertNull(ObservationRss.latestUpdate("<rss><channel>".toByteArray()))
        assertNull(ObservationRss.latestUpdate("not xml at all".toByteArray()))
    }
}
