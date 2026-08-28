package com.mab.aura.core.net

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads AEMET's keyless observation RSS (`obsconv_hh_opendata_todos_RSS.xml`) and returns the newest publish
 * time. This feed is a *freshness notifier*, never a data source: it says when AEMET last refreshed the
 * conventional-observation dataset, so the app can decide whether the keyed `/observacion/convencional/todas`
 * download is worth making, without spending a keyed call to find out.
 *
 * Each `<item>` carries `<description>{"Última actualización": "2026-08-28T11:31:59+0200"}</description>`.
 * That publish time is stamped ~30 min after the hour (11:31 for the 11:00 readings), so it is a DIFFERENT
 * clock from the observation `fint` (top of the hour). The two must never be compared against each other: the
 * publish time drives fetch cadence (compared RSS-to-RSS), the `fint` drives the display gate. See the
 * unified-freshness design.
 *
 * The timestamp is pulled out with a shape-matching regex rather than by JSON-decoding the description: the
 * key carries a non-ASCII accent (charset-fragile across the ISO-8859 payloads AEMET serves), while the
 * timestamp itself is pure ASCII, so matching it directly is the robust choice. The XML is parsed with the
 * shared [hardenedXmlFactory] for the same Android DOCTYPE reason [RSSParser]/[CAPParser] use it.
 */
object ObservationRss {

    // e.g. "2026-08-28T11:31:59+0200" — the offset has no colon, so it needs the `Z` pattern, not ISO_OFFSET.
    private val ISO_INSTANT = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{4}""")
    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)

    /**
     * The newest "Última actualización" across all items, or null when the payload can't be parsed or carries
     * no usable timestamp. Takes the maximum rather than trusting the feed's newest-first ordering, so a
     * reordered feed can't return a stale marker.
     */
    fun latestUpdate(data: ByteArray): Instant? {
        val doc = try {
            hardenedXmlFactory().newDocumentBuilder().parse(ByteArrayInputStream(data))
        } catch (_: Exception) {
            return null
        }
        var latest: Instant? = null
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val description = item.getElementsByTagName("description").item(0)?.textContent ?: continue
            val match = ISO_INSTANT.find(description)?.value ?: continue
            val instant = try {
                OffsetDateTime.parse(match, FORMATTER).toInstant()
            } catch (_: Exception) {
                continue
            }
            if (latest == null || instant.isAfter(latest)) latest = instant
        }
        return latest
    }
}
