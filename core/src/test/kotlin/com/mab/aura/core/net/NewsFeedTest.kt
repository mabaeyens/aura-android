package com.mab.aura.core.net

import com.mab.aura.core.model.NewsItem
import com.mab.aura.core.model.NewsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.time.Instant

/**
 * Parity port of `NewsFeedTests.swift`. RTVE is UTF-8 with English RFC-822 dates and per-item image
 * enclosures; AEMET is ISO-8859-15 with Spanish day/month names and no seconds. These lock the parser,
 * the locale-aware date handling, and the round-robin merge.
 */
class NewsFeedTest {

    private fun t(s: Long): Instant = Instant.ofEpochSecond(s)

    // A trimmed RTVE payload: UTF-8, English date, an image enclosure, and channel-level fields that
    // must NOT be mistaken for an item.
    private val rtveXML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <title>El tiempo</title>
          <link>https://api.rtve.es/api/programas/821</link>
          <pubDate>Fri, 21 Aug 2026 03:58:33 GMT</pubDate>
          <item>
            <enclosure url="https://img.rtve.es/a.jpg" length="1" type="image/jpeg"/>
            <title>Bajada de temperaturas y tormentas</title>
            <link>https://www.rtve.es/noticias/1.shtml</link>
            <pubDate>Fri, 21 Aug 2026 03:58:33 GMT</pubDate>
          </item>
          <item>
            <title>Calor intenso en el este</title>
            <link>https://www.rtve.es/noticias/2.shtml</link>
            <pubDate>Wed, 19 Aug 2026 06:00:00 GMT</pubDate>
          </item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun parsesRTVEItemsAndSkipsChannel() {
        val items = NewsFeed.parse(rtveXML.toByteArray(Charsets.UTF_8), NewsSource.RTVE)
        assertEquals("two <item>s parsed; the channel title/link/pubDate are ignored", 2, items.size)
        assertEquals("Bajada de temperaturas y tormentas", items.first().title)
        assertEquals("https://www.rtve.es/noticias/1.shtml", items.first().link.toString())
        assertEquals("https://img.rtve.es/a.jpg", items.first().imageURL.toString())
        assertEquals(NewsSource.RTVE, items.first().source)
        assertNull("the second item has no enclosure", items.last().imageURL)
    }

    @Test
    fun aemetEncodingAndSpanishDate() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-15"?>
            <rss><channel><item>
              <title>Predicción para la Península</title>
              <link>https://www.aemet.es/es/noticias/x</link>
              <pubDate>lun, 10 ago 2026 06:41 +0000</pubDate>
            </item></channel></rss>
        """.trimIndent()
        // Encode as Latin-1 so "ó" becomes the single byte 0xF3, matching the declared 8-bit encoding.
        val data = xml.toByteArray(Charsets.ISO_8859_1)
        val items = NewsFeed.parse(data, NewsSource.AEMET)
        assertEquals(1, items.size)
        assertEquals(
            "the parser honours the ISO-8859-15 prolog; accents survive",
            "Predicción para la Península",
            items.first().title,
        )
        assertNotNull("the Spanish RFC-822 date (no seconds) parses", items.first().date)
    }

    @Test
    fun parsesCDATAWrappedTitles() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
              <title>Meteored España</title>
              <item>
                <title><![CDATA[España afectada: 10 días de batalla entre masas de aire]]></title>
                <link><![CDATA[https://www.tiempo.com/noticias/uno.html]]></link>
                <pubDate>Fri, 21 Aug 2026 11:51:35 +0000</pubDate>
              </item>
              <item>
                <title><![CDATA[El calor vuelve la semana que viene]]></title>
                <link>https://www.tiempo.com/noticias/dos.html</link>
                <pubDate>Thu, 20 Aug 2026 09:00:00 +0000</pubDate>
              </item>
            </channel></rss>
        """.trimIndent()
        val items = NewsFeed.parse(xml.toByteArray(Charsets.UTF_8), NewsSource.METEORED)
        assertEquals("both CDATA-titled items survive", 2, items.size)
        assertEquals("España afectada: 10 días de batalla entre masas de aire", items.first().title)
        assertEquals(
            "a CDATA-wrapped <link> is unwrapped too",
            "https://www.tiempo.com/noticias/uno.html",
            items.first().link.toString(),
        )
        assertEquals(NewsSource.METEORED, items.first().source)
    }

    @Test
    fun rtveCappedToThreeMostRecent() {
        // Eight daily bulletins, newest (21 Aug) first, one day apart going back.
        val days = listOf("Fri, 21", "Thu, 20", "Wed, 19", "Tue, 18", "Mon, 17", "Sun, 16", "Sat, 15", "Fri, 14")
        val itemsXML = days.mapIndexed { i, day ->
            "<item><title>bulletin $i</title>" +
                "<link>https://www.rtve.es/$i.shtml</link>" +
                "<pubDate>$day Aug 2026 06:00:00 GMT</pubDate></item>"
        }.joinToString("")
        val xml = "<rss><channel>$itemsXML</channel></rss>"
        val items = NewsFeed.parse(xml.toByteArray(Charsets.UTF_8), NewsSource.RTVE)
        assertEquals("RTVE trimmed to its 3 newest items", 3, items.size)
        assertEquals(
            "the three kept are the most recent, newest first",
            listOf("bulletin 0", "bulletin 1", "bulletin 2"),
            items.map { it.title },
        )
    }

    @Test
    fun rfc822BothLocales() {
        assertNotNull(NewsFeed.parseRFC822("Fri, 21 Aug 2026 03:58:33 GMT"))
        assertNotNull(NewsFeed.parseRFC822("lun, 10 ago 2026 06:41 +0000"))
        assertNull(NewsFeed.parseRFC822("not a date"))
        assertNull(NewsFeed.parseRFC822(""))
    }

    @Test
    fun mergeRoundRobinAvoidsSingleSourceDomination() {
        val flood = (0 until 15).map {
            NewsItem("aemet$it", URI("https://a/$it"), NewsSource.AEMET, t(2_000 + it.toLong())) // newest = highest index
        }
        val rtve = (0 until 3).map {
            NewsItem("rtve$it", URI("https://r/$it"), NewsSource.RTVE, t(1_000 + it.toLong()))
        }
        val merged = NewsFeed.merge(listOf(flood, rtve), limit = 20)

        assertEquals("15 + 3 available, under the 20 cap", 18, merged.size)
        assertEquals(
            "all three RTVE items survive despite the AEMET flood",
            3,
            merged.filter { it.source == NewsSource.RTVE }.toSet().size,
        )
        // Stream is time-descending.
        assertEquals(merged.sortedByDescending { it.date }, merged)
    }

    @Test
    fun mergeCutsToLimit() {
        val a = (0 until 30).map { NewsItem("a$it", URI("https://a/$it"), NewsSource.AEMET, t(it.toLong())) }
        val b = (0 until 30).map { NewsItem("b$it", URI("https://b/$it"), NewsSource.RTVE, t(100 + it.toLong())) }
        val merged = NewsFeed.merge(listOf(a, b), limit = 20)
        assertEquals(20, merged.size)
        assertTrue(
            "both sources present even at the cap",
            merged.any { it.source == NewsSource.AEMET } && merged.any { it.source == NewsSource.RTVE },
        )
    }
}
