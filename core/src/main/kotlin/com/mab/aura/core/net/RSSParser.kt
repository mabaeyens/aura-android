package com.mab.aura.core.net

import com.mab.aura.core.model.NewsItem
import com.mab.aura.core.model.NewsSource
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses one source's RSS 2.0 payload into [NewsItem]s and merges per-source streams into one.
 *
 * Direct port of the `NewsFeed` enum and the `RSSParser` delegate in `NewsFeed.swift`. Swift drives
 * Foundation's streaming `XMLParser` (SAX) through a delegate; here the JDK's built-in `javax.xml` DOM is
 * used instead — the same choice [CAPParser] made and for the same reason: Android's `XmlPullParser` needs
 * a device or Robolectric to run, which would break `:core`'s on-JVM unit tests, whereas `javax.xml` ships
 * with the JDK. DOM also does two things the SAX version tracked by hand: `textContent` unwraps CDATA
 * (WordPress feeds wrap `<title>`/`<link>` in it), and `parse(InputStream)` honours the prolog's
 * `encoding=` (AEMET serves ISO-8859-15), so accents survive with no manual re-encoding.
 */
object NewsFeed {
    /**
     * Parse one source's RSS payload into items, dropping any entry missing a title, a valid link or a
     * parseable date (AEMET's *publicaciones* feed, for instance, has empty titles — excluded upstream,
     * but this stays defensive).
     */
    fun parse(data: ByteArray, source: NewsSource): List<NewsItem> {
        val items = RSSParser.parse(data).mapNotNull { raw ->
            val title = raw.title.trim()
            if (title.isEmpty()) return@mapNotNull null
            val link = raw.link.trim().toUriOrNull() ?: return@mapNotNull null
            val date = parseRFC822(raw.pubDate) ?: return@mapNotNull null
            val image = raw.imageURL?.toUriOrNull()
            NewsItem(title = title, link = link, source = source, date = date, imageURL = image)
        }
        // Trim a chatty feed to its most recent entries (RTVE → 3, the daily bulletin plus the two before
        // it), so a single source can't flood the merged stream with backlog.
        val cap = source.maxItems ?: return items
        return items.sortedByDescending { it.date }.take(cap)
    }

    /**
     * Merge per-source items into one stream that is recency-sorted yet never single-source dominated.
     * Selection is round-robin by rank — each source's newest, then each source's second-newest, … — so a
     * source that floods (e.g. an eclipse news burst) can't crowd the others out; the selected set is then
     * sorted by date for a clean time-descending stream, and cut to [limit].
     */
    fun merge(groups: List<List<NewsItem>>, limit: Int = 20): List<NewsItem> {
        val ranked = groups.map { group -> group.sortedByDescending { it.date } }
        val selected = ArrayList<NewsItem>()
        var rank = 0
        outer@ while (selected.size < limit) {
            var addedAny = false
            for (group in ranked) {
                if (rank < group.size) {
                    selected.add(group[rank])
                    addedAny = true
                    if (selected.size >= limit) break@outer
                }
            }
            if (!addedAny) break
            rank++
        }
        return selected.sortedByDescending { it.date }
    }

    /**
     * Parse an RFC-822 `pubDate`. RTVE sends English month/day names ("Fri, 21 Aug 2026 03:58:33 GMT");
     * AEMET sends Spanish ones with no seconds ("lun, 10 ago 2026 06:41 +0000").
     *
     * Swift lists format/locale combinations for `DateFormatter`; a locale-driven `DateTimeFormatter`
     * doesn't port cleanly, because Java's CLDR Spanish month abbreviations carry a trailing dot ("ago.")
     * that these feeds omit, so parsing would silently fail on the AEMET dates. Instead the weekday token
     * (redundant for an instant) is dropped and the remaining `dd MMM yyyy HH:mm[:ss] Z` is tokenised with
     * an explicit English+Spanish month table, which is locale-independent and robust.
     */
    fun parseRFC822(raw: String): Instant? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val tokens = value.replace(",", " ").trim().split(Regex("\\s+"))
        // Drop a leading weekday word ("Fri"/"lun"); if the first token is already the day number, keep it.
        val t = if (tokens.isNotEmpty() && tokens[0].toIntOrNull() == null) tokens.drop(1) else tokens
        if (t.size < 5) return null
        val day = t[0].toIntOrNull() ?: return null
        val month = MONTHS[t[1].lowercase().trimEnd('.')] ?: return null
        val year = t[2].toIntOrNull() ?: return null
        val time = t[3].split(":")
        if (time.size < 2) return null
        val hour = time[0].toIntOrNull() ?: return null
        val minute = time[1].toIntOrNull() ?: return null
        val second = if (time.size >= 3) time[2].toIntOrNull() ?: return null else 0
        val offset = parseOffset(t[4]) ?: return null
        return try {
            LocalDateTime.of(year, month, day, hour, minute, second).toInstant(offset)
        } catch (_: Exception) {
            null
        }
    }

    /** English and Spanish RFC-822 month abbreviations (trailing dot stripped), lowercased → 1…12. */
    private val MONTHS: Map<String, Int> = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "ene" to 1, "abr" to 4, "ago" to 8, "sept" to 9, "dic" to 12,
    )

    /** Parse an RFC-822 zone: numeric offset ("+0000"), the UTC aliases, or a handful of named zones. */
    private fun parseOffset(zone: String): ZoneOffset? {
        val u = zone.trim().uppercase()
        return when {
            u == "GMT" || u == "UTC" || u == "UT" || u == "Z" -> ZoneOffset.UTC
            u.startsWith("+") || u.startsWith("-") -> try {
                val digits = u.drop(1)
                if (digits.length == 4 && digits.all { it.isDigit() }) {
                    ZoneOffset.of("${u[0]}${digits.substring(0, 2)}:${digits.substring(2, 4)}")
                } else {
                    ZoneOffset.of(u)
                }
            } catch (_: Exception) {
                null
            }
            u == "EST" -> ZoneOffset.ofHours(-5)
            u == "EDT" -> ZoneOffset.ofHours(-4)
            u == "PST" -> ZoneOffset.ofHours(-8)
            u == "PDT" -> ZoneOffset.ofHours(-7)
            u == "CET" -> ZoneOffset.ofHours(1)
            u == "CEST" -> ZoneOffset.ofHours(2)
            else -> null
        }
    }

    private fun String.toUriOrNull(): URI? =
        try {
            if (isEmpty()) null else URI(this)
        } catch (_: Exception) {
            null
        }
}

/**
 * Minimal RSS 2.0 item extractor. Reads only the direct children of each `<item>`, so channel-level
 * `<title>`/`<link>`/`<pubDate>` are ignored; namespaced tags (AEMET's `<atom:link>`) keep their prefix in
 * `tagName` and so don't match the bare names, exactly as the Swift SAX version skipped them.
 */
private object RSSParser {
    data class RawItem(
        var title: String = "",
        var link: String = "",
        var pubDate: String = "",
        var imageURL: String? = null,
    )

    fun parse(data: ByteArray): List<RawItem> {
        val doc = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                // Harden against XXE, matching CAPParser: no DOCTYPE, no external entities.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
            }
            factory.newDocumentBuilder().parse(ByteArrayInputStream(data))
        } catch (_: Exception) {
            return emptyList()
        }

        val result = ArrayList<RawItem>()
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val raw = RawItem()
            val children = item.childNodes
            for (j in 0 until children.length) {
                val node = children.item(j) as? Element ?: continue
                when (node.tagName) {
                    "title" -> raw.title = node.textContent ?: ""
                    "link" -> raw.link = node.textContent ?: ""
                    "pubDate" -> raw.pubDate = node.textContent ?: ""
                    "enclosure" -> {
                        // Prefer an image enclosure for the optional thumbnail (RTVE attaches one per item).
                        val url = node.getAttribute("url")
                        val type = node.getAttribute("type")
                        if (url.isNotEmpty() && (type.startsWith("image") || raw.imageURL == null)) {
                            raw.imageURL = url
                        }
                    }
                }
            }
            result.add(raw)
        }
        return result
    }
}
