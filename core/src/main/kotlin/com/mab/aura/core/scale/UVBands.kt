package com.mab.aura.core.scale

/**
 * The WHO UV bands, matching `UVIndex.bandName` / `Palette.uvIndex`, with a protection cue per band.
 * Reference data behind the UV card's tap-through sheet; ported from the `UVBands` enum in
 * `AuraScaleSheets.swift`. The per-band SF Symbol the Swift sheet showed is intentionally left out, exactly
 * as on the UV card (Material core has no such weather glyph, and `UVIndex.glyph` was not ported).
 */
object UVBands {
    data class Band(
        val name: String,
        val lo: Int,
        /** Upper bound of the band; null on the top band, which is open-ended above [lo]. */
        val hi: Int?,
        val advice: String,
    ) {
        /** A representative index for the row's colour, on `Palette.uvIndex`'s ramp. */
        val mid: Int get() = hi?.let { (lo + it) / 2 } ?: (lo + 1)

        /** The band as text: "0–2", "11+" for the open-ended top, "3" when a band spans a single value. */
        val rangeText: String get() = hi?.let { if (lo == it) "$lo" else "$lo–$it" } ?: "$lo+"

        fun contains(value: Int): Boolean = value >= lo && value <= (hi ?: Int.MAX_VALUE)
    }

    val bands: List<Band> = listOf(
        Band("Bajo", 0, 2, "No hace falta protección."),
        Band("Moderado", 3, 5, "Gafas de sol y crema; busca la sombra al mediodía."),
        Band("Alto", 6, 7, "Protección necesaria: crema, gorra y sombra en las horas centrales."),
        Band("Muy alto", 8, 10, "Extrema la protección; evita el sol entre las 12 y las 16 h."),
        Band("Extremadamente alto", 11, null, "Evita el sol; la piel sin proteger se quema en minutos."),
    )
}
