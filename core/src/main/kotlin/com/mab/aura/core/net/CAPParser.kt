package com.mab.aura.core.net

import com.mab.aura.core.model.WeatherAlert
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Parses one AEMET CAP-XML alert file into [WeatherAlert]s — one per warning zone in each Spanish `<info>`
 * block. CAP uses a default namespace with no prefix, so element names are plain.
 *
 * Direct port of `CAPParser` in `WeatherAlert.swift`. Swift drives Foundation's streaming `XMLParser`
 * (SAX) through a delegate; here the JDK's built-in `javax.xml.parsers` DOM is used instead. DOM was
 * chosen over Android's `XmlPullParser`/`android.util.Xml` deliberately: those need a device or Robolectric
 * to run, which would break `:core`'s on-JVM unit tests, whereas `javax.xml` ships with the JDK and needs
 * no dependency. CAP files are tiny (one alert), so reading the whole tree is cheap, and walking direct
 * children keeps each `<parameter>`/`<geocode>` name–value pair correctly scoped (the one thing the SAX
 * version tracked by hand). Parsing is namespace-unaware so the plain element names match the Swift switch;
 * DOCTYPE declarations are rejected to shut off XXE (AEMET's CAP never carries one). A malformed file
 * yields an empty list rather than throwing.
 */
object CAPParser {
    fun parse(xml: ByteArray): List<WeatherAlert> {
        val doc = try {
            // Shared XXE-hardened factory that also parses on Android (see hardenedXmlFactory): the Apache
            // disallow-doctype-decl feature throws on Android's parser, which would otherwise abort every parse
            // on-device and silently drop all alerts, exactly as it did the news feeds.
            hardenedXmlFactory().newDocumentBuilder().parse(ByteArrayInputStream(xml))
        } catch (_: Exception) {
            return emptyList()
        }

        val alerts = ArrayList<WeatherAlert>()
        val infos = doc.getElementsByTagName("info")
        for (i in 0 until infos.length) {
            (infos.item(i) as? Element)?.let { alerts += parseInfo(it) }
        }
        return alerts
    }

    /** One `<info>` block: zero or more alerts (one per warning zone), or empty if it isn't a Spanish
     * block with a recognised level. */
    private fun parseInfo(info: Element): List<WeatherAlert> {
        var language = ""
        var event = ""
        var onset = ""
        var expires = ""
        var nivel = ""
        var parametro = ""
        val zones = ArrayList<Pair<String, String>>()   // (zona code, areaDesc)

        for (child in info.childElements()) {
            when (child.tagName) {
                "language" -> language = child.textTrim()
                "event" -> event = child.textTrim()
                "onset" -> onset = child.textTrim()
                "expires" -> expires = child.textTrim()
                "parameter" -> when (child.childText("valueName")) {
                    "AEMET-Meteoalerta nivel" -> nivel = child.childText("value")
                    "AEMET-Meteoalerta parametro" -> parametro = child.childText("value")
                }
                // Each <area> carries its own <areaDesc> and the <geocode>s scoped to it; pairing them here
                // (rather than against one info-level areaDesc, as the SAX version did) is faithful for
                // AEMET's single-area blocks and correct if an info ever carries more than one.
                "area" -> {
                    val areaDesc = child.childText("areaDesc")
                    for (geo in child.childElements()) {
                        if (geo.tagName != "geocode") continue
                        val name = geo.childText("valueName")
                        val value = geo.childText("value")
                        if (name.lowercase().contains("zona") && value.isNotEmpty()) {
                            zones.add(value to areaDesc)
                        }
                    }
                }
            }
        }

        // Keep only the Spanish info block, and only a recognised meteoalerta level (verde/no-warning and
        // any unknown token drop out).
        if (!language.lowercase().startsWith("es")) return emptyList()
        val level = WeatherAlert.Level.entries.firstOrNull { it.name.equals(nivel, ignoreCase = true) }
            ?: return emptyList()

        // parametro is "tipo;fenómeno;umbral"; the phenomenon is the second field. Swift's split drops empty
        // segments, so mirror that before indexing.
        val phenomenon = parametro.split(";").filter { it.isNotEmpty() }.let { if (it.size >= 2) it[1] else null }
        val onsetDate = parseIso(onset)
        val expiresDate = parseIso(expires)

        return zones.map { (zona, areaDesc) ->
            WeatherAlert(
                level = level, event = event, phenomenon = phenomenon,
                zona = zona, areaDesc = areaDesc, onset = onsetDate, expires = expiresDate,
            )
        }
    }

    /** CAP timestamps are internet date-time with an offset, e.g. "2026-08-21T10:00:00+02:00". */
    private fun parseIso(s: String): Instant? =
        try {
            OffsetDateTime.parse(s).toInstant()
        } catch (_: Exception) {
            null
        }

    // --- Small DOM helpers -------------------------------------------------------------------------

    /** Direct child elements (skipping text/whitespace nodes). */
    private fun Element.childElements(): List<Element> {
        val kids = ArrayList<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let { kids.add(it) }
        return kids
    }

    private fun Element.textTrim(): String = textContent.trim()

    /** The trimmed text of the first direct child element named [tag], or "" when absent. */
    private fun Element.childText(tag: String): String =
        childElements().firstOrNull { it.tagName == tag }?.textTrim() ?: ""
}
