package com.mab.aura.core.icon

/**
 * The distinct weather icons Aura draws, one per branch of AEMET's `estadoCielo` classification. Where the
 * art differs by time of day the glyph carries a `_DAY`/`_NIGHT` variant; the day/night-agnostic states
 * (plain cloud, haze, rain, snow) are single values.
 *
 * This enum is the Android-specific half of the port. On iOS [WeatherIcon] returns SF Symbol *names*
 * (e.g. `"cloud.sun.rain.fill"`); SF Symbols don't exist on Android, so shipping those strings in `:core`
 * would be dead data. Instead the pure mapping (AEMET code → which icon) lives here as a stable enum, and
 * the `:app` layer maps each case to a concrete Material Symbol or bundled drawable once that icon set is
 * chosen (see the plan's Layer E). That keeps the tested logic in `:core` and the asset choice in the UI.
 */
enum class WeatherGlyph {
    CLEAR_DAY, CLEAR_NIGHT,
    FEW_CLOUDS_DAY, FEW_CLOUDS_NIGHT,
    CLOUDY,
    HAZE,
    LIGHT_RAIN_DAY, LIGHT_RAIN_NIGHT,
    RAIN,
    HEAVY_RAIN,
    SNOW,
    LIGHT_SNOW,
    THUNDER_DAY, THUNDER_NIGHT,
    THUNDER_RAIN,
}

/**
 * Maps AEMET's `estadoCielo` codes to a [WeatherGlyph], honouring the night ("n"-suffixed) variants.
 *
 * Direct port of the `WeatherIcon` enum's switch in `WeatherIcon.swift`. The right-hand side (SF Symbol
 * names) becomes [WeatherGlyph] — see that enum for why. The code groupings are transcribed exactly.
 */
object WeatherIcon {
    /** The glyph for a sky-state code (e.g. "11", "13n"). Falls back to a neutral cloud. */
    fun glyph(code: String?): WeatherGlyph {
        if (code.isNullOrEmpty()) return WeatherGlyph.CLOUDY
        val night = code.endsWith("n")
        val base = if (night) code.dropLast(1) else code

        return when (base) {
            "11" -> if (night) WeatherGlyph.CLEAR_NIGHT else WeatherGlyph.CLEAR_DAY
            "12", "13", "17" -> if (night) WeatherGlyph.FEW_CLOUDS_NIGHT else WeatherGlyph.FEW_CLOUDS_DAY
            "14", "15" -> WeatherGlyph.CLOUDY
            "16" -> WeatherGlyph.HAZE
            "23", "43" -> if (night) WeatherGlyph.LIGHT_RAIN_NIGHT else WeatherGlyph.LIGHT_RAIN_DAY
            "24", "44", "45" -> WeatherGlyph.RAIN
            "25", "26", "46" -> WeatherGlyph.HEAVY_RAIN
            "33", "34", "35", "36" -> WeatherGlyph.SNOW     // nieve → the recognised freezing snowflake
            "71", "72", "73", "74" -> WeatherGlyph.LIGHT_SNOW // nieve escasa (light snow) → cloud with flakes
            "51", "52", "61", "62" -> if (night) WeatherGlyph.THUNDER_NIGHT else WeatherGlyph.THUNDER_DAY
            "53", "54", "63", "64" -> WeatherGlyph.THUNDER_RAIN
            else -> if (night) WeatherGlyph.FEW_CLOUDS_NIGHT else WeatherGlyph.FEW_CLOUDS_DAY
        }
    }

    /**
     * The glyph for a sky-state code, but forcing the day or night variant from a caller that knows the
     * actual time of day (from the location's sun times) rather than the AEMET code's own suffix.
     */
    fun glyph(code: String?, isNight: Boolean): WeatherGlyph {
        if (code.isNullOrEmpty()) return if (isNight) WeatherGlyph.CLEAR_NIGHT else WeatherGlyph.CLEAR_DAY
        val base = if (code.endsWith("n")) code.dropLast(1) else code
        return glyph(code = if (isNight) base + "n" else base)
    }
}
