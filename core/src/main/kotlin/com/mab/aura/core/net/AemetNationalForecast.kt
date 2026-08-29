package com.mab.aura.core.net

import com.mab.aura.core.model.ForecastBulletin
import com.mab.aura.core.model.MedioPlazoForecast
import java.time.Instant
import java.time.ZoneId

/**
 * The "Predicción nacional" card's data source: AEMET's España-level normalized-text forecast, the national
 * twin of the community bulletin ([comunidadBulletin]). AEMET publishes four national products under
 * `/prediccion/nacional/…`, served as the same `text/plain` two-step `datos` envelope the community bulletin
 * uses, so this reuses [AemetClient.fetchText] and — for three of the four — the very same
 * [AemetBulletinParser].
 *
 * The four products, and how each is parsed:
 * - `hoy`, `manana`, `pasadomanana` carry the identical `A.- FENÓMENOS SIGNIFICATIVOS` / `B.- PREDICCIÓN`
 *   skeleton as the community bulletin, so they parse straight into [ForecastBulletin].
 * - `medioplazo` is different: no A/B sections, just a run of `DÍA NN (DIANOMBRE)` day blocks, so it has its
 *   own [AemetMedioPlazoParser] and [MedioPlazoForecast] model.
 *
 * Kept as extension functions to mirror `comunidadBulletin` (and Swift's `public extension AEMETClient`); they
 * only use the public `fetchText` engine. The request-budget rule — only `hoy` rides the refresh path (gated
 * to ≤1/6h), the other three fetch lazily when the sheet opens — lives in the app-side repository, not here.
 */

/** Europe/Madrid — the civil time AEMET stamps these bulletins in (same as the community bulletin). */
private val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

/** The four national text products, with their `/prediccion/nacional/{path}` segment. */
enum class NationalProduct(val path: String) {
    HOY("hoy"),
    MANANA("manana"),
    PASADO_MANANA("pasadomanana"),
    MEDIO_PLAZO("medioplazo"),
}

/** The resolved national forecast for today, plus the raw text it came from (so the caller can cache it). */
data class NationalToday(val bulletin: ForecastBulletin, val raw: String)

/** The raw `ascii/txt` payload for one national product. */
suspend fun AemetClient.nationalText(product: NationalProduct): String =
    fetchText("/prediccion/nacional/${product.path}")

/**
 * The national narrative that covers *today*.
 *
 * Like the community `hoy`, the national `hoy` is an *amendment* channel — only re-issued when conditions
 * change intraday, so on a quiet day it can name a date days back (confirmed 5 days stale in a live sample
 * while `manana` was current). So this prefers `hoy` only when AEMET issued one valid for today; otherwise it
 * shows the current `manana` product as today. National has **no** per-day archive fallback like the CCAA
 * bulletin's `…/elaboracion/{ayer}` path, so `manana` (valid for tomorrow) is the best "today" we can offer on
 * a stale-`hoy` day — the card's freshness line still carries the real elaborado/validity so the reader sees
 * which day the words actually cover.
 *
 * Returns null only when neither product could be fetched or parsed. [at] defaults to now, in Madrid time.
 */
suspend fun AemetClient.nationalToday(at: Instant = Instant.now()): NationalToday? {
    val today = at.atZone(MADRID).toLocalDate()

    // 1. Prefer an intraday `hoy` amendment, but only when it is actually valid for today. A network or parse
    //    failure isn't fatal — it just falls through to `manana`.
    val hoyRaw = runCatching { nationalText(NationalProduct.HOY) }.getOrNull()
    if (hoyRaw != null) {
        val hoy = AemetBulletinParser.parse(hoyRaw)
        if (hoy != null && hoy.validezInicio?.atZone(MADRID)?.toLocalDate() == today) {
            return NationalToday(hoy, hoyRaw)
        }
    }

    // 2. Otherwise the current `manana` product stands in as today (no archive fallback exists nationally).
    val mananaRaw = runCatching { nationalText(NationalProduct.MANANA) }.getOrNull() ?: return null
    val manana = AemetBulletinParser.parse(mananaRaw) ?: return null
    return NationalToday(manana, mananaRaw)
}

/** The `manana` national bulletin (valid for tomorrow), or null on a fetch/parse failure. */
suspend fun AemetClient.nationalManana(): ForecastBulletin? =
    runCatching { nationalText(NationalProduct.MANANA) }.getOrNull()?.let(AemetBulletinParser::parse)

/** The `pasadomanana` national bulletin (valid for the day after tomorrow), or null on failure. */
suspend fun AemetClient.nationalPasadoManana(): ForecastBulletin? =
    runCatching { nationalText(NationalProduct.PASADO_MANANA) }.getOrNull()?.let(AemetBulletinParser::parse)

/** The `medioplazo` national outlook (the days beyond that), split per day, or null on failure. */
suspend fun AemetClient.nationalMedioPlazo(): MedioPlazoForecast? =
    runCatching { nationalText(NationalProduct.MEDIO_PLAZO) }.getOrNull()?.let(AemetMedioPlazoParser::parse)

/**
 * Parses AEMET's national medium-range outlook (`/prediccion/nacional/medioplazo`). Unlike the community
 * bulletin this has no A/B sections; after the four header lines the body is a run of per-day blocks:
 *
 *     AGENCIA ESTATAL DE METEOROLOGÍA
 *     PREDICCIÓN GENERAL DE MEDIO PLAZO PARA ESPAÑA
 *     DÍA 29 DE AGOSTO DE 2026 A LAS 14:21 HORA OFICIAL
 *     PREDICCIÓN VÁLIDA PARA LOS DÍAS 1 Y 2 DE SEPTIEMBRE DE 2026
 *
 *     DÍA 01 (MARTES)
 *      Se mantendrá una situación de estabilidad …
 *
 *     DÍA 02 (MIÉRCOLES)
 *      Se prevé que se mantenga …
 *
 * Each `DÍA NN (DIANOMBRE)` header opens a block; its narrative is hard-wrapped and unfolded into paragraphs,
 * exactly as the community bulletin unfolds section B. If no header matches (an unexpected layout), the whole
 * post-header body is returned as a single unnamed block rather than nothing.
 */
object AemetMedioPlazoParser {

    // "DÍA 01 (MARTES)". D[IÍ]A matches either spelling; the day is the two-digit number, the name is whatever
    // sits in the parentheses. Anchored to the whole (trimmed) line so a "DÍA …" inside prose never matches.
    private val dayHeaderRegex = Regex("""^D[IÍ]A\s+(\d{1,2})\s*\(([^)]+)\)\s*$""", RegexOption.IGNORE_CASE)

    // "DÍA 29 DE AGOSTO DE 2026 A LAS 14:21 HORA OFICIAL" — the elaboration stamp (same shape the community
    // bulletin uses; parsed here just for the sheet's freshness line, so a miss is harmless).
    private val issueRegex = Regex(
        """D[IÍ]A\s+(\d{1,2})\s+DE\s+(\p{L}+)\s+DE\s+(\d{4})\s+A\s+LAS\s+(\d{1,2}):(\d{2})""",
        RegexOption.IGNORE_CASE,
    )

    private val months: Map<String, Int> = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5, "junio" to 6,
        "julio" to 7, "agosto" to 8, "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12,
    )

    fun parse(raw: String): MedioPlazoForecast? {
        val lines = raw.replace("\r\n", "\n").split("\n").map { it.trim() }

        val issueLine = lines.firstOrNull { it.uppercase().contains("HORA OFICIAL") }
        val validezLine = lines.firstOrNull { it.uppercase().startsWith("PREDICCIÓN VÁLIDA") }
        val elaborado = issueLine?.let(::issueDate)

        // Where the first day header appears; everything from here down is the per-day body.
        val firstHeader = lines.indexOfFirst { dayHeaderRegex.matches(it) }

        val days = if (firstHeader < 0) {
            // No day header at all: keep whatever body we can (everything after the four-line preamble), as one
            // unnamed block, rather than dropping the outlook entirely.
            val bodyStart = lines.indexOfFirst { it.uppercase().startsWith("PREDICCIÓN VÁLIDA") }.let {
                if (it >= 0) it + 1 else 0
            }
            val texto = unfold(lines.subList(bodyStart.coerceIn(0, lines.size), lines.size))
            if (texto.isEmpty()) return null
            listOf(MedioPlazoForecast.Day(dia = 0, diaNombre = "", texto = texto))
        } else {
            buildDays(lines, firstHeader)
        }

        if (days.isEmpty()) return null
        return MedioPlazoForecast(elaborado = elaborado, validez = validezLine, days = days)
    }

    /** Walk the lines from [firstHeader], opening a new [MedioPlazoForecast.Day] at each `DÍA NN` header. */
    private fun buildDays(lines: List<String>, firstHeader: Int): List<MedioPlazoForecast.Day> {
        val days = mutableListOf<MedioPlazoForecast.Day>()
        var dia = 0
        var nombre = ""
        val body = mutableListOf<String>()

        fun flush() {
            if (nombre.isEmpty() && body.all { it.isBlank() }) return
            val texto = unfold(body)
            if (texto.isNotEmpty()) days.add(MedioPlazoForecast.Day(dia = dia, diaNombre = nombre, texto = texto))
            body.clear()
        }

        for (i in firstHeader until lines.size) {
            val line = lines[i]
            val header = dayHeaderRegex.matchEntire(line)
            if (header != null) {
                flush() // close the previous day before starting this one
                dia = header.groupValues[1].toIntOrNull() ?: 0
                nombre = header.groupValues[2].trim()
            } else {
                body.add(line)
            }
        }
        flush()
        return days
    }

    /**
     * Unfold hard-wrapped lines: blank lines delimit paragraphs; within a paragraph the wraps are joined with
     * spaces; paragraphs are rejoined with a blank line. Same rule as [AemetBulletinParser]'s section unfolding
     * (kept local so this parser stays self-contained).
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

    private fun issueDate(line: String): Instant? {
        val m = issueRegex.find(line) ?: return null
        val (dayS, monthS, yearS, hourS, minuteS) = m.destructured
        val day = dayS.toIntOrNull() ?: return null
        val month = months[monthS.lowercase()] ?: return null
        val year = yearS.toIntOrNull() ?: return null
        val hour = hourS.toIntOrNull() ?: return null
        val minute = minuteS.toIntOrNull() ?: return null
        return runCatching {
            java.time.LocalDateTime.of(year, month, day, hour, minute).atZone(MADRID).toInstant()
        }.getOrNull()
    }
}
