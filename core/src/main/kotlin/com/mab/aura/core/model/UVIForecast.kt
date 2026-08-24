package com.mab.aura.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `uvi/{dia}` payload: one object (not the array most `/prediccion` products use) with an issue/
 * validity header and one entry per provincial capital.
 *
 * Direct port of the `UVIForecast` decodable in `UVIndex.swift`. The `UVIndex` band-name/advice logic
 * from that same Swift file is pure logic and ports separately into `core/uv/` in a later phase; this
 * file is only the wire model. Swift's `CodingKeys` for the shouty header keys becomes [@SerialName].
 */
@Serializable
data class UVIForecast(
    @SerialName("FECHA_VALIDEZ") val fechaValidez: String? = null,
    @SerialName("CIUDAD") val ciudad: List<City>,
) {
    @Serializable
    data class City(
        /** INE municipio code, e.g. "28079" for Madrid. */
        val id: String,
        /** Display name, e.g. "Madrid". */
        val valor: String? = null,
        /** The daily-max UV index, as a string integer. */
        val uv: String,
    )
}
