package com.mab.aura.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mab.aura.core.model.WeatherAlert
import com.mab.aura.core.sky.SkyCategory
import com.mab.aura.core.sky.SkyCode
import java.time.LocalTime

/**
 * Aura's shared colour system, ported from `Palette.swift`. The app and every widget draw from here, so a
 * given temperature or sky condition looks the same on each surface.
 *
 * Split from the Swift original: the pure `sky(forCode:)` categorisation moved to `:core` as [SkyCode]
 * (the logic [ForecastPhrase] and others need), and only the `Color`/gradient code — genuinely UI — lives
 * here in `:app`. This object holds that UI half.
 *
 * Android notes for someone coming from SwiftUI:
 * - SwiftUI's `Color(red:green:blue:)` takes `Double`; Compose's [Color] takes `Float`, so every channel
 *   literal carries an `f` suffix.
 * - SwiftUI `LinearGradient(colors:startPoint:.top, endPoint:.bottom)` becomes [Brush.verticalGradient],
 *   which draws top-to-bottom by default — the same direction Aura's sky and header gradients use.
 * - SwiftUI's `Gradient` (a bare colour list handed to a `Gauge` or range bar) has no Compose type; the
 *   scale helpers below return `List<Color>` so a caller can build whatever brush the layout needs.
 *
 * The watch-complication-only helpers from the Swift file (`precipComplication`, `precipComplicationScale`)
 * are intentionally dropped: Android has no watch surface (see the port plan, Section 4). The app keeps the
 * stepped [precip] it always used.
 */
object Palette {

    /** One control stop on a colour ramp: a threshold [t] and its red/green/blue channels. */
    private class ColorStop(val t: Float, val r: Float, val g: Float, val b: Float)

    // MARK: Temperature → colour

    /**
     * Temperature colour, blue (very cold) through deep red (terribly hot). Continuously interpolated
     * between control stops, not banded — every degree gets a distinct colour and the progression reads
     * as one smooth ramp. Tuned to the scale AEMET and TVE's "El Tiempo" use: blues and greens up to 20°,
     * then warm tones above, with the green→yellow boundary at 20° and red around 30°.
     */
    fun temperature(celsius: Int?): Color {
        val t = celsius ?: return Color.Gray
        return lerp(t.toFloat(), tempStops)
    }

    /**
     * Control stops: (°C, red, green, blue). Between two stops the colour is linearly interpolated. The 20°
     * green/yellow hand-off is the anchor of the TVE/AEMET scale; the hot end stays a deep legible maroon
     * rather than the reference's literal black, which would vanish as tinted text.
     */
    private val tempStops = listOf(
        ColorStop(-8f, 0.40f, 0.16f, 0.56f),   // violet — extreme cold
        ColorStop(-2f, 0.24f, 0.28f, 0.74f),   // blue-violet
        ColorStop(4f, 0.20f, 0.52f, 0.90f),    // blue
        ColorStop(10f, 0.24f, 0.74f, 0.82f),   // cyan-teal
        ColorStop(15f, 0.34f, 0.76f, 0.55f),   // green-teal
        ColorStop(19f, 0.46f, 0.77f, 0.37f),   // green
        ColorStop(22f, 0.74f, 0.80f, 0.30f),   // green-yellow (boundary at 20°)
        ColorStop(25f, 0.97f, 0.83f, 0.26f),   // yellow
        ColorStop(28f, 0.98f, 0.65f, 0.20f),   // amber
        ColorStop(31f, 0.96f, 0.47f, 0.18f),   // orange
        ColorStop(34f, 0.90f, 0.30f, 0.20f),   // red-orange
        ColorStop(37f, 0.80f, 0.19f, 0.19f),   // red
        ColorStop(41f, 0.60f, 0.11f, 0.16f),   // dark red
        ColorStop(45f, 0.40f, 0.07f, 0.13f),   // deep maroon — the scale's "black" end, kept legible
    )

    /**
     * Linear-interpolate a colour for [x] across `(threshold, r, g, b)` stops, clamped at both ends.
     * Shared by the temperature and wind ramps.
     */
    private fun lerp(x: Float, stops: List<ColorStop>): Color {
        val first = stops.first()
        val last = stops.last()
        if (x <= first.t) return Color(first.r, first.g, first.b)
        if (x >= last.t) return Color(last.r, last.g, last.b)
        for (i in 0 until stops.size - 1) {
            val a = stops[i]
            val b = stops[i + 1]
            if (x >= a.t && x <= b.t) {
                val k = (x - a.t) / (b.t - a.t)
                return Color(
                    a.r + (b.r - a.r) * k,
                    a.g + (b.g - a.g) * k,
                    a.b + (b.b - a.b) * k,
                )
            }
        }
        return Color.Gray
    }

    // MARK: Wind speed → colour

    /**
     * Wind-speed colour, calm (pale blue) through gale (red) to violent storm (violet), continuously
     * interpolated — modelled on Windy's wind scale so a glance at the vane reads intensity. Values in
     * km/h, the unit AEMET reports.
     */
    fun wind(kmh: Int?): Color {
        val v = kmh ?: return tempTeal
        return lerp(v.toFloat(), windStops)
    }

    private val windStops = listOf(
        ColorStop(0f, 0.55f, 0.80f, 0.92f),     // calm — pale blue
        ColorStop(12f, 0.30f, 0.80f, 0.72f),    // light — teal
        ColorStop(22f, 0.42f, 0.80f, 0.40f),    // moderate — green
        ColorStop(32f, 0.86f, 0.84f, 0.28f),    // fresh — yellow
        ColorStop(45f, 0.97f, 0.62f, 0.20f),    // strong — orange
        ColorStop(60f, 0.92f, 0.34f, 0.22f),    // very strong — red-orange
        ColorStop(80f, 0.84f, 0.18f, 0.28f),    // gale — red
        ColorStop(105f, 0.66f, 0.20f, 0.62f),   // storm — magenta
        ColorStop(130f, 0.52f, 0.28f, 0.78f),   // violent — violet
    )

    // MARK: Air quality (ICA category → colour)

    /**
     * The official MITECO ICA colour for a 1–6 category: blue (buena) → green → yellow → red → dark red →
     * violet (extremadamente desfavorable). Falls back to grey for an out-of-range/no-data value.
     */
    fun airQuality(category: Int): Color = when (category) {
        1 -> Color(0.31f, 0.66f, 0.93f)   // buena — azul
        2 -> Color(0.30f, 0.72f, 0.42f)   // razonablemente buena — verde
        3 -> Color(0.96f, 0.80f, 0.25f)   // regular — amarillo
        4 -> Color(0.90f, 0.29f, 0.24f)   // desfavorable — rojo
        5 -> Color(0.60f, 0.13f, 0.15f)   // muy desfavorable — granate
        6 -> Color(0.60f, 0.28f, 0.75f)   // extremadamente desfavorable — violeta
        else -> Color(0.55f, 0.55f, 0.55f)
    }

    // MARK: UV index → colour

    /** The WHO UV-index colour: green (bajo) → yellow → orange → red → violet (extremo), by band. */
    fun uvIndex(value: Int): Color = when {
        value < 3 -> Color(0.30f, 0.72f, 0.42f)    // bajo — verde
        value <= 5 -> Color(0.96f, 0.80f, 0.25f)   // moderado — amarillo
        value <= 7 -> Color(0.97f, 0.58f, 0.18f)   // alto — naranja
        value <= 10 -> Color(0.90f, 0.29f, 0.24f)  // muy alto — rojo
        else -> Color(0.60f, 0.28f, 0.75f)         // extremo — violeta
    }

    /**
     * Probability of precipitation (0…100 %) → blue. There is no official POP colour scale, so this is an
     * Aura convention: paler for a slim chance, deeper as it climbs, reusing the temperature blues so the
     * app stays one family. Read it as "more blue = more likely".
     */
    fun precip(prob: Int): Color = when {
        prob < 20 -> Color(0.62f, 0.84f, 1.0f)   // muy baja — azul pálido
        prob < 50 -> tempBlue                     // baja/media
        prob < 80 -> tempDeepBlue                 // alta
        else -> Color(0.10f, 0.20f, 0.62f)        // muy alta — azul intenso
    }

    // MARK: Named temperature colours

    // Declared before the scale lists below, because Kotlin initialises an object's properties top-to-
    // bottom: `temperatureGradient` reads these eagerly, so they must already hold their values by then.
    val tempDeepBlue = Color(0.16f, 0.28f, 0.78f)
    val tempBlue = Color(0.25f, 0.52f, 0.93f)
    val tempTeal = Color(0.20f, 0.74f, 0.80f)
    val tempGreen = Color(0.30f, 0.72f, 0.42f)
    val tempYellow = Color(0.96f, 0.80f, 0.25f)
    val tempOrange = Color(0.97f, 0.58f, 0.18f)
    val tempRed = Color(0.90f, 0.29f, 0.24f)
    val tempPurple = Color(0.60f, 0.28f, 0.75f)

    /**
     * Night moon glyph — a clear cool blue that reads on both a black face and a light surface well, rather
     * than the flat pale white multicolour gives.
     */
    val nightMoon = Color(0.42f, 0.55f, 0.96f)

    // MARK: Temperature scales (for gauge tints and range bars)

    /**
     * The full cold→hot scale, for gauge tints and range bars. SwiftUI returned a `Gradient`; Compose has
     * no bare gradient type, so this is a plain colour list the caller turns into whatever brush it needs.
     */
    val temperatureGradient: List<Color> = listOf(
        tempDeepBlue, tempBlue, tempTeal, tempGreen, tempYellow, tempOrange, tempRed, tempPurple,
    )

    /**
     * The temperature scale sampled across just `[lo, hi]`, so a range bar shows the colours that actually
     * apply — e.g. 24°→34° runs yellow→orange→red, not the whole blue→purple palette. One stop per degree
     * keeps each colour aligned to its position along the bar.
     */
    fun temperatureGradient(min: Int, max: Int): List<Color> {
        if (min >= max) return listOf(temperature(min))
        return (min..max).map { temperature(it) }
    }

    // MARK: Time of day → gradient

    /** An r/g/b anchor colour on the day-cycle ramp, in the 0…1 float channels Compose uses. */
    private class Rgb(val r: Float, val g: Float, val b: Float) {
        fun color() = Color(r, g, b)
    }

    /** A day-cycle anchor: the hour [h] it applies at, and the sky's [top] and [bot]tom colours then. */
    private class DayAnchor(val h: Float, val top: Rgb, val bot: Rgb)

    /**
     * A sky-coloured gradient that tracks the time of day: light blue in the morning, electric blue at
     * midday, a darker warm-violet at dusk, deep blue at night. Interpolated between hourly anchors so it
     * drifts smoothly. White text stays legible on every phase. Used behind the "Hoy" header card.
     *
     * SwiftUI defaulted to `Date()`; here [time] defaults to the current local time-of-day. Pass a fixed
     * [LocalTime] from a `@Preview` or test to pin the gradient to a chosen hour.
     */
    fun timeGradient(time: LocalTime = LocalTime.now()): Brush {
        val (top, bottom) = timeColors(time)
        return Brush.verticalGradient(listOf(top, bottom))
    }

    /**
     * Day-cycle anchors: (hour, topRGB, bottomRGB). The 24h anchor mirrors 0h so the loop wraps.
     */
    private val dayAnchors = listOf(
        DayAnchor(0f, Rgb(0.05f, 0.07f, 0.20f), Rgb(0.10f, 0.13f, 0.30f)),   // night
        DayAnchor(7f, Rgb(0.24f, 0.42f, 0.76f), Rgb(0.40f, 0.60f, 0.90f)),   // dawn — light blue
        DayAnchor(13f, Rgb(0.10f, 0.40f, 0.90f), Rgb(0.26f, 0.56f, 0.96f)),  // zenith — electric blue
        DayAnchor(19f, Rgb(0.16f, 0.20f, 0.44f), Rgb(0.40f, 0.28f, 0.46f)),  // dusk — darker, warm hint
        DayAnchor(24f, Rgb(0.05f, 0.07f, 0.20f), Rgb(0.10f, 0.13f, 0.30f)),  // night (wrap)
    )

    /**
     * The sky gradient's top and bottom colours for a given time — the base sky behind `AuraSky`, before
     * the sun glow and scenery are layered on. Exposes the same day-cycle anchors [timeGradient] uses.
     */
    fun skyBaseColors(time: LocalTime = LocalTime.now()): Pair<Color, Color> = timeColors(time)

    private fun timeColors(time: LocalTime): Pair<Color, Color> {
        val h = time.hour + time.minute / 60f
        for (i in 0 until dayAnchors.size - 1) {
            val a = dayAnchors[i]
            val b = dayAnchors[i + 1]
            if (h >= a.h && h <= b.h) {
                val k = (h - a.h) / (b.h - a.h)
                fun lerpRgb(x: Rgb, y: Rgb) = Color(
                    x.r + (y.r - x.r) * k,
                    x.g + (y.g - x.g) * k,
                    x.b + (y.b - x.b) * k,
                )
                return lerpRgb(a.top, b.top) to lerpRgb(a.bot, b.bot)
            }
        }
        val n = dayAnchors[0]
        return n.top.color() to n.bot.color()
    }

    // MARK: Sky condition → gradient

    /**
     * A top-to-bottom background gradient for a sky code — the mood colour behind a card. Night variants go
     * deep and desaturated. Categorisation is [SkyCode.classify] in `:core`; the colours are here.
     */
    fun skyGradient(code: String?): Brush {
        val (category, isNight) = SkyCode.classify(code)
        return Brush.verticalGradient(skyColors(category, isNight))
    }

    private fun skyColors(category: SkyCategory, isNight: Boolean): List<Color> {
        if (isNight) {
            return when (category) {
                SkyCategory.CLEAR, SkyCategory.FEW_CLOUDS ->
                    listOf(Color(0.06f, 0.10f, 0.28f), Color(0.12f, 0.16f, 0.38f))
                SkyCategory.CLOUDS, SkyCategory.OVERCAST ->
                    listOf(Color(0.14f, 0.16f, 0.24f), Color(0.20f, 0.22f, 0.30f))
                SkyCategory.RAIN, SkyCategory.STORM ->
                    listOf(Color(0.10f, 0.12f, 0.22f), Color(0.18f, 0.18f, 0.30f))
                SkyCategory.SNOW ->
                    listOf(Color(0.16f, 0.20f, 0.30f), Color(0.26f, 0.30f, 0.40f))
                SkyCategory.FOG ->
                    listOf(Color(0.18f, 0.20f, 0.24f), Color(0.26f, 0.28f, 0.32f))
                SkyCategory.UNKNOWN ->
                    listOf(Color(0.10f, 0.13f, 0.24f), Color(0.18f, 0.20f, 0.30f))
            }
        }
        return when (category) {
            SkyCategory.CLEAR ->
                listOf(Color(0.20f, 0.52f, 0.92f), Color(0.45f, 0.72f, 0.98f))
            SkyCategory.FEW_CLOUDS ->
                listOf(Color(0.30f, 0.56f, 0.90f), Color(0.56f, 0.74f, 0.94f))
            SkyCategory.CLOUDS ->
                listOf(Color(0.42f, 0.53f, 0.66f), Color(0.60f, 0.68f, 0.78f))
            SkyCategory.OVERCAST ->
                listOf(Color(0.40f, 0.46f, 0.54f), Color(0.56f, 0.61f, 0.68f))
            SkyCategory.RAIN ->
                listOf(Color(0.30f, 0.40f, 0.55f), Color(0.46f, 0.56f, 0.68f))
            SkyCategory.STORM ->
                listOf(Color(0.26f, 0.28f, 0.42f), Color(0.40f, 0.42f, 0.55f))
            SkyCategory.SNOW ->
                listOf(Color(0.60f, 0.70f, 0.82f), Color(0.80f, 0.86f, 0.94f))
            SkyCategory.FOG ->
                listOf(Color(0.55f, 0.59f, 0.63f), Color(0.72f, 0.75f, 0.78f))
            SkyCategory.UNKNOWN ->
                listOf(Color(0.30f, 0.50f, 0.78f), Color(0.50f, 0.66f, 0.86f))
        }
    }

    /**
     * How dark the frosted cards should ride for a sky, as the black opacity at the card's top and (heavier)
     * bottom. The hero glow is brightest at the horizon, under the lowest cards, so bright skies (clear, few
     * clouds) get the full gradient, mid skies (clouds, fog, snow) a moderate one, and skies already grey or
     * dark (overcast, rain, storm, any night) barely any. Ported from Swift `Palette.cardScrim(forCode:)`;
     * [AuraForecastStack][com.mab.aura.ui.cards.AuraForecastStack] wraps this in an `AuraCardScrim`.
     */
    fun cardScrim(code: String?): Pair<Float, Float> {
        val (category, isNight) = SkyCode.classify(code)
        if (isNight) return 0.0f to 0.08f            // sky already dark — leave it be
        return when (category) {
            SkyCategory.CLEAR, SkyCategory.FEW_CLOUDS -> 0.10f to 0.34f   // bright — the colours need the lift
            SkyCategory.CLOUDS, SkyCategory.FOG, SkyCategory.SNOW -> 0.05f to 0.22f  // mid — a moderate scrim
            SkyCategory.OVERCAST, SkyCategory.RAIN -> 0.0f to 0.12f       // already grey — a whisper
            SkyCategory.STORM -> 0.0f to 0.10f
            SkyCategory.UNKNOWN -> 0.06f to 0.24f    // the previous fixed default
        }
    }

    /** An accent tint for a sky condition — the icon/foreground colour that reads on a neutral card. */
    fun skyAccent(code: String?): Color {
        val (category, isNight) = SkyCode.classify(code)
        if (isNight) return Color(0.62f, 0.68f, 0.86f)
        return when (category) {
            SkyCategory.CLEAR, SkyCategory.FEW_CLOUDS -> tempYellow
            SkyCategory.CLOUDS, SkyCategory.OVERCAST -> Color(0.52f, 0.58f, 0.66f)
            SkyCategory.RAIN -> tempBlue
            SkyCategory.STORM -> tempPurple
            SkyCategory.SNOW -> tempTeal
            SkyCategory.FOG -> Color(0.60f, 0.63f, 0.66f)
            SkyCategory.UNKNOWN -> tempBlue
        }
    }

    // MARK: Alert level → colour

    /** The colour for an avisos severity level (verde/amarillo/naranja/rojo). */
    fun alert(level: WeatherAlert.Level): Color = when (level) {
        WeatherAlert.Level.VERDE -> tempGreen
        WeatherAlert.Level.AMARILLO -> tempYellow
        WeatherAlert.Level.NARANJA -> tempOrange
        WeatherAlert.Level.ROJO -> tempRed
    }
}
