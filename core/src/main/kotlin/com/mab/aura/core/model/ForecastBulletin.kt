package com.mab.aura.core.model

import java.time.Instant

/**
 * The official narrative forecast for an autonomous community, as issued by an AEMET forecaster and served
 * from the OpenData normalized-text products.
 *
 * Direct port of the `ForecastBulletin` struct in `AEMETBulletin.swift` — the model half only. The text
 * parser and the `comunidadBulletin`/`hoy`-vs-`manana` client logic from that Swift file are Layer C (the
 * net layer) and are not ported here; [WeatherSnapshot.make] only needs [texto] and [fenomenoSignificativo].
 * Swift's `Date` becomes [java.time.Instant]. Not persisted (Swift is `Sendable`, not `Codable`), so this is
 * a plain data class with no `@Serializable`.
 */
data class ForecastBulletin(
    /** When AEMET produced this bulletin (from the "DÍA … A LAS … HORA OFICIAL" header). */
    val elaborado: Instant? = null,
    /** The day the bulletin is valid for (from the "PREDICCIÓN VÁLIDA PARA …" header). */
    val validezInicio: Instant? = null,
    /** End of the validity window. AEMET's daily bulletins cover a single day, so this is null. */
    val validezFin: Instant? = null,
    /** Significant phenomena (section "A.- FENÓMENOS SIGNIFICATIVOS"), or null when none are expected. */
    val fenomenoSignificativo: String? = null,
    /** The main narrative text (section "B.- PREDICCIÓN"), hard wraps unfolded into paragraphs. */
    val texto: String,
)
