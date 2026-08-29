package com.mab.aura.core.model

import java.time.Instant

/**
 * AEMET's national *medium-range* forecast (`/prediccion/nacional/medioplazo`): a multi-day España-level
 * outlook that covers the days beyond tomorrow.
 *
 * Unlike the `hoy`/`manana`/`pasadomanana` national products — which share the community bulletin's
 * `A.- FENÓMENOS SIGNIFICATIVOS` / `B.- PREDICCIÓN` skeleton and so reuse [ForecastBulletin] — medioplazo has
 * no A/B sections. Its body is a run of per-day blocks, each headed by a `DÍA NN (DIANOMBRE)` line (confirmed
 * from a live sample: `DÍA 01 (MARTES)`, `DÍA 02 (MIÉRCOLES)`). So it gets its own small model, filled by
 * `AemetMedioPlazoParser` in `net/AemetNationalForecast.kt`.
 *
 * Not persisted (fetched lazily and cached in memory by the repository, never folded into the disk-cached
 * [WeatherSnapshot]), so this is a plain data class with no `@Serializable`.
 */
data class MedioPlazoForecast(
    /** When AEMET produced this outlook (from the "DÍA … A LAS … HORA OFICIAL" header). */
    val elaborado: Instant? = null,
    /** The human validity line as AEMET wrote it, e.g. "PREDICCIÓN VÁLIDA PARA LOS DÍAS 1 Y 2 DE SEPTIEMBRE
     *  DE 2026". Kept verbatim (Spanish, data) for the sheet's freshness line; we don't re-derive it. */
    val validez: String? = null,
    /** One entry per `DÍA NN (DIANOMBRE)` block, in the order AEMET lists them. */
    val days: List<Day>,
) {
    /** A single day of the medium-range outlook. */
    data class Day(
        /** The day-of-month from the header (`01` → 1). */
        val dia: Int,
        /** The weekday name as AEMET wrote it, e.g. "MARTES" (Spanish, data). */
        val diaNombre: String,
        /** The day's narrative, hard wraps unfolded into paragraphs. */
        val texto: String,
    )
}
