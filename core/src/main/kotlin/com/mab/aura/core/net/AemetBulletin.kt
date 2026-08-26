package com.mab.aura.core.net

import com.mab.aura.core.geo.Comunidad
import com.mab.aura.core.model.ForecastBulletin
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The "Predicción" card's data source: AEMET's official, human-written community forecast bulletin.
 *
 * Layer C port of the second half of the Swift `AEMETBulletin.swift` — the `comunidadBulletin` client logic
 * and the `AEMETBulletinParser`. The model half ([ForecastBulletin]) was ported earlier; this is what fills
 * it. AEMET serves these as a small fixed-layout `ascii/txt` document (not JSON), so the parser works on the
 * raw text [AemetClient.fetchText] returns.
 *
 * Android note: Swift leaned on `Calendar`/`DateFormatter` pinned to Europe/Madrid and `Date`. The Kotlin
 * equivalents are `java.time` — [LocalDate]/[LocalDateTime] resolved through the [MADRID] zone into the
 * [Instant]s [ForecastBulletin] stores. AEMET always stamps these bulletins in Spanish peninsular civil
 * time, so every date here is interpreted in that zone regardless of the device's own.
 */

/** Europe/Madrid — the civil time AEMET stamps its bulletins in. */
private val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

/** `yyyy-MM-dd` in Spanish peninsular time, for the archive endpoint's `elaboracion` segment. */
private val ARCHIVE_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * The official community narrative that covers *today*, from AEMET's OpenData text products.
 *
 * AEMET's `hoy` product is an *amendment* channel — it is only re-issued when conditions change
 * significantly intraday, so on a quiet day it can name a date days back. The forecast that actually covers
 * today was issued *yesterday* as the daily `manana` product. So this prefers today's `hoy` when AEMET issued
 * one valid for today, and otherwise reads yesterday's `manana` from the archive
 * (`…/elaboracion/{ayer}`), which is guaranteed to cover today.
 *
 * [Comunidad.code] is AEMET's community code (e.g. "mad", "gal"). [at] defaults to now, interpreted in
 * Spanish peninsular civil time.
 *
 * Kept as an extension function to mirror Swift's `public extension AEMETClient`; it only uses the public
 * [AemetClient.fetchText] engine, so it needs nothing private from the client.
 */
suspend fun AemetClient.comunidadBulletin(comunidad: Comunidad, at: Instant = Instant.now()): ForecastBulletin {
    val today = at.atZone(MADRID).toLocalDate()

    // 1. Prefer an intraday `hoy` amendment, but only if it is actually valid for today. Swift's `try?` — a
    //    network or parse failure here is not fatal, it just falls through to the archive path.
    val hoy = runCatching { fetchText("/prediccion/ccaa/hoy/${comunidad.code}") }
        .getOrNull()
        ?.let(AemetBulletinParser::parse)
    if (hoy != null && hoy.validezInicio?.atZone(MADRID)?.toLocalDate() == today) {
        return hoy
    }

    // 2. Otherwise, yesterday's `manana` is today's forecast.
    val yesterday = today.minusDays(1)
    val elaboracion = yesterday.format(ARCHIVE_DAY)
    val text = fetchText("/prediccion/ccaa/manana/${comunidad.code}/elaboracion/$elaboracion")
    return AemetBulletinParser.parse(text)
        ?: throw AemetClientException.Decoding("community bulletin text was not in the expected format")
}

/**
 * Parses AEMET's normalized-text community bulletin (a small fixed-layout `ascii/txt` document):
 *
 *     AGENCIA ESTATAL DE METEOROLOGÍA
 *     PREDICCIÓN GENERAL PARA LA COMUNIDAD DE …
 *     DÍA 18 DE AGOSTO DE 2026 A LAS 12:38 HORA OFICIAL
 *     PREDICCIÓN VÁLIDA PARA EL MIÉRCOLES 19
 *
 *     A.- FENÓMENOS SIGNIFICATIVOS
 *     …
 *
 *     B.- PREDICCIÓN
 *     …
 *
 * Lines are hard-wrapped to a narrow column; each section's wraps are unfolded back into flowing paragraphs
 * (blank lines separate paragraphs; single newlines are wraps). Direct port of the Swift
 * `AEMETBulletinParser` enum.
 */
object AemetBulletinParser {

    fun parse(raw: String): ForecastBulletin? {
        val lines = raw.replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }

        val issueLine = lines.firstOrNull { it.uppercase().contains("HORA OFICIAL") }
        val validezLine = lines.firstOrNull { it.uppercase().startsWith("PREDICCIÓN VÁLIDA") }

        // Split the body into the "A.-" and "B.-" sections.
        val aIndex = lines.indexOfFirst { it.startsWith("A.-") }
        if (aIndex < 0) return null
        val bIndex = lines.indexOfFirst { it.startsWith("B.-") }.takeIf { it >= 0 }

        val aBody = if (bIndex != null) lines.subList(aIndex + 1, bIndex) else lines.subList(aIndex + 1, lines.size)
        val bBody = if (bIndex != null) lines.subList(bIndex + 1, lines.size) else emptyList()

        val elaborado = issueLine?.let(::issueDate)
        val validez = validezLine?.let { validezDate(it, elaborado) }

        val fenomeno = unfold(aBody)
        val texto = unfold(bBody)
        if (texto.isEmpty()) return null

        return ForecastBulletin(
            elaborado = elaborado,
            validezInicio = validez,
            validezFin = null,
            fenomenoSignificativo = if (isNoPhenomena(fenomeno)) null else fenomeno,
            texto = texto,
        )
    }

    /**
     * Unfold hard-wrapped lines: blank lines delimit paragraphs; within a paragraph the wraps are joined with
     * spaces. Paragraphs are rejoined with a blank line.
     */
    private fun unfold(lines: List<String>): String {
        val paragraphs = mutableListOf<String>()
        val current = mutableListOf<String>()
        for (line in lines) {
            if (line.isEmpty()) {
                if (current.isNotEmpty()) {
                    paragraphs.add(current.joinToString(" "))
                    current.clear()
                }
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) paragraphs.add(current.joinToString(" "))
        return paragraphs.joinToString("\n\n").trim()
    }

    /** True when section A says there are no significant phenomena (e.g. "No se esperan."). */
    private fun isNoPhenomena(text: String): Boolean {
        val low = text.lowercase()
        if (low.isEmpty()) return true
        return low.startsWith("no se esperan") ||
            low.contains("sin fenómenos") ||
            low.contains("no hay fenómenos") ||
            low.contains("ningún fenómeno")
    }

    private val months: Map<String, Int> = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5, "junio" to 6,
        "julio" to 7, "agosto" to 8, "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12,
    )

    // "DÍA 18 DE AGOSTO DE 2026 A LAS 12:38 HORA OFICIAL". `D[IÍ]A` matches either spelling; IGNORE_CASE also
    // handles a lowercased month. \p{L} is a Unicode letter (so "AGOSTO" and accented months both match).
    private val issueRegex = Regex(
        """D[IÍ]A\s+(\d{1,2})\s+DE\s+(\p{L}+)\s+DE\s+(\d{4})\s+A\s+LAS\s+(\d{1,2}):(\d{2})""",
        RegexOption.IGNORE_CASE,
    )

    private fun issueDate(line: String): Instant? {
        val m = issueRegex.find(line) ?: return null
        val (dayS, monthS, yearS, hourS, minuteS) = m.destructured
        val day = dayS.toIntOrNull() ?: return null
        val month = months[monthS.lowercase()] ?: return null
        val year = yearS.toIntOrNull() ?: return null
        val hour = hourS.toIntOrNull() ?: return null
        val minute = minuteS.toIntOrNull() ?: return null
        return instant(year, month, day, hour, minute)
    }

    // The validity day is the trailing number of "PREDICCIÓN VÁLIDA PARA EL MIÉRCOLES 19".
    private val validezRegex = Regex("""(\d{1,2})\s*$""")

    /**
     * Parse the validity day; the month/year come from the issue date (rolling to the next month when the
     * validity day precedes the issue day, e.g. issued on the 31st, valid for the 1st).
     */
    private fun validezDate(line: String, reference: Instant?): Instant? {
        if (reference == null) return null
        val validDay = validezRegex.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val refDate = reference.atZone(MADRID).toLocalDate()
        var year = refDate.year
        var month = refDate.monthValue
        if (validDay < refDate.dayOfMonth) { // crossed into the next month
            month += 1
            if (month > 12) { month = 1; year += 1 }
        }
        return instant(year, month, validDay, 0, 0)
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant? =
        runCatching {
            LocalDateTime.of(year, month, day, hour, minute).atZone(MADRID).toInstant()
        }.getOrNull()
}
