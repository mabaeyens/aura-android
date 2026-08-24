package com.mab.aura.core.model

import kotlinx.serialization.Serializable

/**
 * AEMET's hourly municipal forecast (`prediccion/especifica/municipio/horaria/{ine}`).
 *
 * Each [Dia] carries parallel hourly arrays keyed by `periodo` (the hour, "00"–"23"), except
 * `probPrecipitacion`, which AEMET reports in coarser multi-hour blocks (periodo like "0814").
 * All values arrive as strings. `precipitacion` and `nieve` are the hourly *amounts* in mm (keyed by
 * the single hour, same as `temperatura`); a value can be "Ip" (precipitación inapreciable — a trace),
 * which reads as ~0. They're optional because some municipal responses omit them.
 *
 * Direct port of `MunicipioHourly.swift`. The four always-present arrays (`temperatura`,
 * `estadoCielo`, `humedadRelativa`, `probPrecipitacion`) are required; the rest carry a `= null`
 * default so a missing key decodes as absent (see the note in [MunicipioForecast]).
 */
@Serializable
data class MunicipioHourly(
    val nombre: String,
    val provincia: String,
    val prediccion: Prediccion,
) {
    @Serializable
    data class Prediccion(
        val dia: List<Dia>,
    )

    @Serializable
    data class Dia(
        val fecha: String,
        val orto: String? = null,
        val ocaso: String? = null,
        val temperatura: List<HourValue>,
        val estadoCielo: List<SkyValue>,
        val humedadRelativa: List<HourValue>,
        val probPrecipitacion: List<HourValue>,
        /** Feels-like temperature, hourly (°C, same keying as `temperatura`). */
        val sensTermica: List<HourValue>? = null,
        /** Rain and snow *amounts*, hourly, in mm (single-hour periodo). "Ip" = a trace. */
        val precipitacion: List<HourValue>? = null,
        val nieve: List<HourValue>? = null,
        /** Storm and snow probabilities, in the same coarse multi-hour blocks as `probPrecipitacion`. */
        val probTormenta: List<HourValue>? = null,
        val probNieve: List<HourValue>? = null,
        /**
         * Wind and peak gust, hour by hour. Mixed entries: wind entries carry `direccion`+`velocidad`
         * (single-element string arrays), gust entries only a scalar `value`. Optional — some
         * municipal responses omit it.
         */
        val vientoAndRachaMax: List<WindValue>? = null,
    )

    /** A wind reading (`direccion`+`velocidad`) or a gust (`value`) at `periodo`. */
    @Serializable
    data class WindValue(
        val periodo: String,
        val direccion: List<String>? = null,
        val velocidad: List<String>? = null,
        val value: String? = null,
    )

    /** An hourly (or block) reading: the string `value` at `periodo`. */
    @Serializable
    data class HourValue(
        val value: String,
        val periodo: String,
    )

    /** Sky state: a code (e.g. "11", "11n" at night) plus AEMET's Spanish description. */
    @Serializable
    data class SkyValue(
        val value: String,
        val periodo: String,
        val descripcion: String? = null,
    )
}
