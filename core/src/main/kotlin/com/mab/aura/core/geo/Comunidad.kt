package com.mab.aura.core.geo

import com.mab.aura.core.model.Location

/**
 * A Spanish autonomous community, carrying the code AEMET's OpenData API uses (e.g. "mad").
 *
 * Direct port of `Comunidad` in `Comunidad.swift`. Aura reads the community narrative from OpenData's
 * normalized-text products, keyed by this [code] (see the future `BulletinService`). Swift's `Sendable,
 * Hashable` map to a plain Kotlin `data class` (immutable, with structural `equals`/`hashCode`).
 */
data class Comunidad(
    /** AEMET OpenData area code, e.g. "mad". */
    val code: String,
    /** Display name, e.g. "Comunidad de Madrid". */
    val nombre: String,
) {
    companion object {
        /** All 17 communities plus Ceuta and Melilla, keyed by AEMET code. */
        private val byCode: Map<String, Comunidad> = mapOf(
            "and" to Comunidad("and", "Andalucía"),
            "arn" to Comunidad("arn", "Aragón"),
            "ast" to Comunidad("ast", "Principado de Asturias"),
            "bal" to Comunidad("bal", "Illes Balears"),
            "can" to Comunidad("can", "Cantabria"),
            "cat" to Comunidad("cat", "Cataluña"),
            "ceu" to Comunidad("ceu", "Ciudad de Ceuta"),
            "cle" to Comunidad("cle", "Castilla y León"),
            "clm" to Comunidad("clm", "Castilla-La Mancha"),
            "coo" to Comunidad("coo", "Canarias"),
            "ext" to Comunidad("ext", "Extremadura"),
            "gal" to Comunidad("gal", "Galicia"),
            "mad" to Comunidad("mad", "Comunidad de Madrid"),
            "mel" to Comunidad("mel", "Ciudad de Melilla"),
            "mur" to Comunidad("mur", "Región de Murcia"),
            "nav" to Comunidad("nav", "Comunidad Foral de Navarra"),
            "pva" to Comunidad("pva", "País Vasco"),
            "rio" to Comunidad("rio", "La Rioja"),
            "val" to Comunidad("val", "Comunitat Valenciana"),
        )

        /** 2-digit INE province code → AEMET CCAA code, for all 52 provinces. */
        private val provinceToCCAA: Map<String, String> = mapOf(
            "01" to "pva", "02" to "clm", "03" to "val", "04" to "and", "05" to "cle", "06" to "ext",
            "07" to "bal", "08" to "cat", "09" to "cle", "10" to "ext", "11" to "and", "12" to "val",
            "13" to "clm", "14" to "and", "15" to "gal", "16" to "clm", "17" to "cat", "18" to "and",
            "19" to "clm", "20" to "pva", "21" to "and", "22" to "arn", "23" to "and", "24" to "cle",
            "25" to "cat", "26" to "rio", "27" to "gal", "28" to "mad", "29" to "and", "30" to "mur",
            "31" to "nav", "32" to "gal", "33" to "ast", "34" to "cle", "35" to "coo", "36" to "gal",
            "37" to "cle", "38" to "coo", "39" to "can", "40" to "cle", "41" to "and", "42" to "cle",
            "43" to "cat", "44" to "arn", "45" to "clm", "46" to "val", "47" to "cle", "48" to "pva",
            "49" to "cle", "50" to "arn", "51" to "ceu", "52" to "mel",
        )

        /** The community a 2-digit INE province code belongs to, if known. */
        fun forProvincia(provinciaCode: String): Comunidad? =
            provinceToCCAA[provinciaCode]?.let { byCode[it] }
    }
}

/**
 * The autonomous community this municipality belongs to.
 *
 * Swift declares this as a computed property in a `Location` extension; the Kotlin equivalent is an
 * extension property, kept here alongside [Comunidad] rather than on [Location] itself so `:core`'s model
 * layer stays free of the geo lookup table.
 */
val Location.comunidad: Comunidad?
    get() = Comunidad.forProvincia(provinciaCode)
