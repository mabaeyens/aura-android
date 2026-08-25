package com.mab.aura.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.mab.aura.core.lunar.MoonPhaseMath
import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.sky.AuraSunPath as CoreSunPath
import com.mab.aura.core.sky.SkyCategory
import com.mab.aura.core.sky.SkyCode
import com.mab.aura.ui.drawPhasedMoon
import com.mab.aura.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Aura's signature background: a full-bleed sky whose light sits where the sun (or moon) actually is for
 * the location and the hour. The sun **rises on the left (east)**, climbs to the top at solar noon, and
 * **sets on the right (west)**; after dark a dimmer moon glow arcs across a star-scattered night. The sky
 * condition never moves the light — it only draws a veil over it, so a cloudy morning and a cloudy evening
 * are lit from opposite sides. Ported from `AuraSky.swift`.
 *
 * Position is computed once from the [now] passed in (no timer): recompute it on each appearance/refresh,
 * which is plenty — nobody stares at the screen for a full minute.
 *
 * Two Swift features are intentionally **not** ported here yet:
 * - The **hero image** overlay (`heroImage`/`heroCarriesCondition`/`heroHorizon`/`clampedLight`): the 8×6
 *   art bytes are the separate "Appendix A" problem and aren't in the repo, so there is nothing to draw. The
 *   procedural sky below is the complete, correct fallback the Swift code uses when no hero image is set;
 *   the hero overlay + horizon-clamp lands with those assets.
 * - The Watch surface (this is phone-only). [compact] is kept because the Glance widgets (a later layer)
 *   will pass it to tame the glow on a small card.
 *
 * Android notes for someone coming from SwiftUI:
 * - The whole SwiftUI `ZStack` of layers becomes one Compose [Canvas], drawing each layer in order into a
 *   single `DrawScope`. That is simpler here than replicating positioned sub-views and per-view blend modes.
 * - SwiftUI's `.blur(radius:).opacity(_:)` on the sun/moon view becomes a `GraphicsLayer` with a
 *   [BlurEffect] and an alpha — the idiomatic Compose way to blur and fade a *group* of draw calls together.
 * - A bare [Canvas] emits no accessibility node, so nothing needs hiding from TalkBack; the SwiftUI
 *   `.accessibilityHidden(true)` has no equivalent to write here.
 */
@Composable
fun AuraSky(
    snapshot: WeatherSnapshot?,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    compact: Boolean = false,
) {
    val path = AuraSunPath.from(now, snapshot?.sunrise, snapshot?.sunset)
    val category = SkyCode.classify(snapshot?.currentSky).category
    val veil = veil(category)                       // how much cloud dulls the light, 0…1
    val hidesDisc = hidesDisc(category)             // heavy skies show glow but no defined disc
    val nowLocal = now.atZone(ZoneId.systemDefault()).toLocalTime()
    val base = Palette.skyBaseColors(nowLocal)
    val sun = glowColor(path.isNight, path.altitude)
    val scene = sceneColors(path.isNight, path.altitude, sun)
    // Tonight's moon phase, so the night sky reflects how much of the moon is actually lit. Only meaningful
    // at night; illumination pinned to 0 by day so the sun path is untouched.
    val moonFraction = MoonPhaseMath.fraction(now)
    val moonIllum = if (path.isNight) MoonPhaseMath.illumination(moonFraction) else 0.0
    val moonWaxing = MoonPhaseMath.waxing(moonFraction)
    val precipKind = precip(category)

    // The graphics layer that blurs + fades the sun/moon disc as one group (see the class doc).
    val discLayer = rememberGraphicsLayer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // The disc radius, capped so the sun/moon reads at a consistent physical size, then shrunk toward
        // the horizon: a rising/setting sun on a ridge reads better small. Full at noon, ~0.62× at horizon.
        val lowSun = (0.62 + 0.38 * path.altitude).toFloat()
        val discBase = if (compact) min(min(w, h) * 0.07f, 20f) else min(min(w, h) * 0.075f, 32f)
        val discR = discBase * lowSun
        // No hero image (yet) → the light sits at the true solar path point, no horizon clamp.
        val centre = Offset(path.point.x * w, path.point.y * h)

        // 1 — the sky itself: the procedural top-to-bottom gradient that tracks the hour.
        drawRect(brush = Brush.verticalGradient(listOf(base.first, base.second)))

        // 2 — the cloud veil: a soft, slightly cool scrim that greys the sky as it clouds over. A
        // neutral-cool grey (not warm) keeps an overcast noon from reading muddy/brown.
        if (veil > 0.0) {
            val veilColor = if (path.isNight) Color(0.12f, 0.12f, 0.12f) else Color(0.60f, 0.65f, 0.72f)
            drawRect(color = veilColor.copy(alpha = (veil * 0.5).toFloat()))
        }

        // 2.5 — night dim: a subtle darkening so night reads as night, not dusk. Deepest at the middle of
        // the night (moon highest), gentle toward dawn and dusk. It sits *under* the moon glow, so the
        // moonlit pool still lifts back out of it; kept light so it never crushes the scene to black.
        if (path.isNight) {
            val nightDim = (0.10 + path.altitude * 0.12).toFloat()   // ~0.10 at the edges → ~0.22 at midnight
            drawRect(color = Color(0.02f, 0.03f, 0.09f).copy(alpha = nightDim))
        }

        // 3 — the light: a warm (or cool, at night) glow centred exactly where the sun/moon is. Day glow
        // eases off as the sun climbs, so the gold doesn't overpower the noon blue (which read as a green
        // cast); it stays strong low on the horizon. On a compact surface the halo is pulled in and dimmed.
        val glowPeak = ((if (path.isNight) 0.55 * moonIllum else 0.92 - path.altitude * 0.30) *
            (1 - veil * 0.5) * (if (compact) 0.62 else 1.0)).toFloat()
        val glowRadius = if (compact) min(w, h) * 1.1f else max(w, h) * 0.78f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(sun.copy(alpha = glowPeak), sun.copy(alpha = 0f)),
                center = centre,
                radius = glowRadius,
            ),
        )

        // 3.5 — the light source itself: a defined sun (or moon) disc with a soft corona, at the true
        // position. Cloud doesn't just dim it: the same `veil` occludes it — the disc shrinks and its blur
        // swells, so rain/storm/fog read as the sun *hidden* behind weather. Occlusion is alpha + radius +
        // blur only; the disc never leaves the true solar position.
        val occlusion = veil
        val occludedR = discR * (1 - occlusion * 0.35).toFloat()          // smaller under cloud, full when clear
        // The moon is markedly dimmer than the day disc and gets a soft base blur even on the clearest
        // night, so its edge stays a gentle pale coin rather than a hard, sun-bright point.
        val discAlpha = (if (path.isNight) 0.62 else 1.0) * (1 - occlusion * 0.85)
        val discBlur = discR * ((if (path.isNight) 0.08 else 0.05) + occlusion * 0.9).toFloat()
        // Overcast, rain, storm, snow and fog never resolve into a disc you can point at — the glow bleeds
        // through, but the defined core and corona are dropped. Only clear/few-clouds/cloudy keep a ball.
        if (discAlpha > 0.02 && !hidesDisc) {
            val disc = discColors(path.isNight, path.altitude, sun)

            // Corona — a wide soft halo; fades and tightens as the disc is occluded. Drawn `SrcOver` at
            // night (Screen would over-brighten the dark sky), `Screen` by day.
            val coronaR = discR * (1 - occlusion * 0.2).toFloat()
            val coronaAlpha = ((if (path.isNight) 0.60 * moonIllum else 0.55) * discAlpha).toFloat()
            val coronaSpread = if (path.isNight) 3.4f else 3.2f
            if (coronaAlpha > 0.01f) {
                // SwiftUI's RadialGradient(startRadius: coronaR*0.7, endRadius: coronaR*spread) is a solid
                // core out to 0.7/spread of the radius, then a fade to transparent — expressed here as stops.
                val startFrac = 0.7f / coronaSpread
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to disc.glow.copy(alpha = coronaAlpha),
                            startFrac to disc.glow.copy(alpha = coronaAlpha),
                            1f to disc.glow.copy(alpha = 0f),
                        ),
                        center = centre,
                        radius = coronaR * coronaSpread,
                    ),
                    radius = coronaR * coronaSpread,
                    center = centre,
                    blendMode = if (path.isNight) BlendMode.SrcOver else BlendMode.Screen,
                )
            }

            // Record the disc into a layer so the blur + alpha apply to it as one group.
            discLayer.record {
                if (path.isNight) {
                    // The moon at its real phase for tonight: an ashen earthshine body with the lit limb on
                    // top, waxing lit on the right. A faint cool white reads against the night sky.
                    drawPhasedMoon(
                        center = centre,
                        radius = occludedR,
                        illumination = moonIllum,
                        waxing = moonWaxing,
                        litColor = Color(0.94f, 0.96f, 1.0f),
                    )
                } else {
                    // The sun — bright core to warm rim, lit slightly off-centre for depth. SwiftUI centred
                    // the gradient at UnitPoint(0.42, 0.38) inside the disc box; mapped to canvas space that
                    // is the disc centre shifted by (-0.16, -0.24) of the radius.
                    val gCenter = Offset(centre.x - 0.16f * occludedR, centre.y - 0.24f * occludedR)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(disc.core, disc.rim),
                            center = gCenter,
                            radius = occludedR,
                        ),
                        radius = occludedR,
                        center = centre,
                    )
                }
            }
            discLayer.alpha = discAlpha.toFloat()
            // BlurEffect requires a positive radius; a near-zero clear-sky blur is left off (sharp disc).
            discLayer.renderEffect = if (discBlur > 0.5f) BlurEffect(discBlur, discBlur) else null
            drawLayer(discLayer)
        }

        // 4 — stars, night only. Condition is the main driver (clear nights show the most, cloud hides
        // them) and a bright moon washes a few more out (new moon: none lost; full: about a third).
        if (path.isNight) {
            val starAlpha = ((1 - veil * 0.8) * (1 - moonIllum * 0.35)).toFloat()
            if (starAlpha > 0.01f) drawStars(starAlpha)
        }

        // 5 — the flat vector scenery along the horizon: mountains, hills, a sun-lit river, and trees whose
        // shadows lean away from the sun.
        drawScenery(scene, sunX = path.point.x)

        // 6 — precipitation. Static rain streaks (heavier for a storm) or snow flecks, so the sky matches
        // its own condition. Only rain/storm/snow fall; the rest are just cloud, handled by the veil.
        if (precipKind != null) drawPrecip(precipKind)
    }
}

// MARK: - Sun / moon position

/**
 * Where the light source sits on screen, and whether it's night. Pure and testable. Ported from the
 * `AuraSunPath` struct in `AuraSky.swift`; `:core` already holds the [CoreSunPath.onSameDay] date helper
 * this reuses, and this rendering-side position maths lands here with the composable as `:core` intended.
 */
class AuraSunPath private constructor(
    /** The light's position in unit space: x 0 = leading/east, 1 = trailing/west; y 0 = top. */
    val point: Offset,
    /** True between sunset and the next sunrise. */
    val isNight: Boolean,
    /** The light's height above the horizon, 0 (horizon) → 1 (solar noon / moon's arc peak). */
    val altitude: Double,
) {
    companion object {
        private const val SECONDS_PER_DAY = 86_400.0
        private const val MILLIS_PER_DAY = 86_400_000L
        private val NEUTRAL_NOON = AuraSunPath(Offset(0.5f, 0.16f), isNight = false, altitude = 1.0)

        fun from(now: Instant, sunrise: Instant?, sunset: Instant?): AuraSunPath {
            // No sun times (polar edge case / missing data): a neutral high-noon sky.
            if (sunrise == null || sunset == null) return NEUTRAL_NOON
            // Decide day/night against the render day, not the (possibly older) day the snapshot was built.
            val sr = CoreSunPath.onSameDay(now, sunrise)
            val ss = CoreSunPath.onSameDay(now, sunset)
            if (!ss.isAfter(sr)) return NEUTRAL_NOON

            val nowMs = now.toEpochMilli()
            val srMs = sr.toEpochMilli()
            val ssMs = ss.toEpochMilli()

            return if (!now.isBefore(sr) && !now.isAfter(ss)) {
                val f = (nowMs - srMs).toDouble() / (ssMs - srMs)   // 0 at sunrise → 1 at sunset
                val alt = sin(f * PI)                                // 0 → 1 → 0 across the day
                AuraSunPath(Offset(f.toFloat(), (0.80 - alt * 0.66).toFloat()), isNight = false, altitude = alt)
            } else {
                // Night: fraction from this sunset to the next sunrise. Before dawn we're past *yesterday's*
                // sunset (sun times barely move day to day, so today's stand in).
                val dayLength = (ssMs - srMs) / 1000.0
                val nightLength = max(SECONDS_PER_DAY - dayLength, 1.0)
                val since = if (!now.isBefore(ss)) (nowMs - ssMs) / 1000.0
                            else (nowMs - (ssMs - MILLIS_PER_DAY)) / 1000.0
                val g = (since / nightLength).coerceIn(0.0, 1.0)
                val alt = sin(g * PI)
                AuraSunPath(Offset(g.toFloat(), (0.60 - alt * 0.40).toFloat()), isNight = true, altitude = alt)
            }
        }
    }
}

// MARK: - Condition → how much the light is veiled

private fun veil(category: SkyCategory): Double = when (category) {
    SkyCategory.CLEAR -> 0.00
    SkyCategory.FEW_CLOUDS -> 0.18
    SkyCategory.CLOUDS -> 0.45
    SkyCategory.OVERCAST -> 0.62
    SkyCategory.FOG -> 0.68
    SkyCategory.RAIN -> 0.66
    SkyCategory.STORM -> 0.72
    SkyCategory.SNOW -> 0.50
    SkyCategory.UNKNOWN -> 0.10
}

/**
 * Whether this sky is too veiled to ever show a *defined* sun/moon disc. Overcast, fog, rain, storm and
 * snow read as an even deck — a glow at most — at every hour. Clear, few-clouds and (dimmed) cloudy keep a
 * real disc; unknown falls back to drawing one.
 */
private fun hidesDisc(category: SkyCategory): Boolean = when (category) {
    SkyCategory.OVERCAST, SkyCategory.FOG, SkyCategory.RAIN, SkyCategory.STORM, SkyCategory.SNOW -> true
    SkyCategory.CLEAR, SkyCategory.FEW_CLOUDS, SkyCategory.CLOUDS, SkyCategory.UNKNOWN -> false
}

/** The kind of precipitation to draw, or null for a dry sky. */
private enum class Precip { RAIN, STORM, SNOW }

private fun precip(category: SkyCategory): Precip? = when (category) {
    SkyCategory.RAIN -> Precip.RAIN
    SkyCategory.STORM -> Precip.STORM
    SkyCategory.SNOW -> Precip.SNOW
    else -> null
}

// MARK: - Colours

/** A 0…1 RGB triple, for the light/scenery colour ramps. Interpolated in Double, converted to [Color]. */
private class Rgb(val r: Double, val g: Double, val b: Double) {
    fun color() = Color(r.toFloat(), g.toFloat(), b.toFloat())

    companion object {
        fun lerp(a: Rgb, b: Rgb, k: Double) =
            Rgb(a.r + (b.r - a.r) * k, a.g + (b.g - a.g) * k, a.b + (b.b - a.b) * k)
    }
}

/**
 * The glow colour: pale moonlight at night; warm gold high in the day, deepening to orange as the sun nears
 * the horizon at dawn and dusk.
 */
private fun glowColor(isNight: Boolean, altitude: Double): Color {
    if (isNight) return Color(0.76f, 0.80f, 0.96f)
    val horizon = Rgb(1.00, 0.60, 0.34)   // low sun — warm orange
    val noon = Rgb(1.00, 0.93, 0.72)      // high sun — bright gold
    return Rgb.lerp(horizon, noon, altitude).color()
}

private class DiscColors(val core: Color, val rim: Color, val glow: Color)

/**
 * The sun/moon disc's own colours: a bright core, a warm (or cool, at night) rim, and the corona tint. The
 * daytime core stays near-white so the disc reads as a light source, deepening its rim to orange near the
 * horizon; the moon is a pale silver.
 */
private fun discColors(isNight: Boolean, altitude: Double, glow: Color): DiscColors {
    if (isNight) {
        // A pale, cool silver — the core held back off pure white so the moon reads as reflected light.
        return DiscColors(
            core = Rgb(0.90, 0.92, 0.99).color(),
            rim = Rgb(0.78, 0.82, 0.97).color(),
            glow = Rgb(0.76, 0.80, 0.96).color(),
        )
    }
    val rimHorizon = Rgb(1.00, 0.55, 0.28)   // low sun — orange rim
    val rimNoon = Rgb(1.00, 0.88, 0.60)      // high sun — soft gold rim
    return DiscColors(
        core = Rgb(1.00, 0.99, 0.94).color(),
        rim = Rgb.lerp(rimHorizon, rimNoon, altitude).color(),
        glow = glow,
    )
}

private class SceneColors(
    val far: Color,
    val near: Color,
    val water: Color,
    val tree: Color,
    val trunk: Color,
)

/** Scenery tints. Daytime scenery warms toward dusk as the sun drops; night goes near-silhouette. */
private fun sceneColors(isNight: Boolean, altitude: Double, glow: Color): SceneColors {
    if (isNight) {
        return SceneColors(
            far = Rgb(0.11, 0.14, 0.28).color(),
            near = Rgb(0.07, 0.09, 0.20).color(),
            water = glow.copy(alpha = 0.26f),
            tree = Rgb(0.08, 0.12, 0.22).color(),
            trunk = Rgb(0.05, 0.08, 0.17).color(),
        )
    }
    // Blend a dusk palette (altitude 0) into a daytime palette (altitude 1).
    val farDusk = Rgb(0.34, 0.30, 0.52); val farDay = Rgb(0.34, 0.50, 0.78)
    val nearDusk = Rgb(0.16, 0.16, 0.34); val nearDay = Rgb(0.17, 0.34, 0.62)
    val treeDusk = Rgb(0.16, 0.22, 0.34); val treeDay = Rgb(0.17, 0.46, 0.37)
    val trunkDusk = Rgb(0.16, 0.17, 0.26); val trunkDay = Rgb(0.26, 0.31, 0.35)
    val k = altitude
    return SceneColors(
        far = Rgb.lerp(farDusk, farDay, k).color(),
        near = Rgb.lerp(nearDusk, nearDay, k).color(),
        water = glow.copy(alpha = 0.5f),
        tree = Rgb.lerp(treeDusk, treeDay, k).color(),
        trunk = Rgb.lerp(trunkDusk, trunkDay, k).color(),
    )
}

// MARK: - Drawing

/** A deterministic pseudo-random value in [0, 1) from an integer seed — a cheap hash so precipitation and
 *  stars land in the same places on every render (the sky never animates). */
private fun frac(n: Int): Float {
    val x = sin(n.toDouble() * 12.9898) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** Fixed star field, in unit coordinates: (x, y, radius-in-pixels). */
private val stars: List<Triple<Float, Float, Float>> = listOf(
    Triple(0.08f, 0.10f, 0.9f), Triple(0.17f, 0.22f, 0.7f), Triple(0.24f, 0.08f, 1.0f), Triple(0.33f, 0.18f, 0.7f),
    Triple(0.41f, 0.28f, 0.8f), Triple(0.48f, 0.12f, 0.9f), Triple(0.55f, 0.24f, 0.7f), Triple(0.62f, 0.09f, 1.0f),
    Triple(0.68f, 0.20f, 0.7f), Triple(0.74f, 0.30f, 0.8f), Triple(0.80f, 0.11f, 0.9f), Triple(0.87f, 0.23f, 0.7f),
    Triple(0.93f, 0.14f, 0.9f), Triple(0.12f, 0.33f, 0.6f), Triple(0.36f, 0.36f, 0.6f), Triple(0.58f, 0.34f, 0.6f),
    Triple(0.71f, 0.38f, 0.6f), Triple(0.90f, 0.34f, 0.6f),
)

private fun DrawScope.drawStars(alpha: Float) {
    val color = Color.White.copy(alpha = 0.85f * alpha)
    for ((sx, sy, r) in stars) {
        drawCircle(color = color, radius = r, center = Offset(sx * size.width, sy * size.height))
    }
}

private fun DrawScope.drawScenery(colors: SceneColors, sunX: Float) {
    val w = size.width
    val h = size.height
    val band = h * 0.46f                 // a taller scenery band, so the horizon sits higher up the screen
    val top = h - band                   // and shows through/behind the lower cards

    // A soft haze just above the horizon lifts the ridges off the sky and adds depth.
    val hazeTop = top - band * 0.14f
    val hazeHeight = band * 0.62f
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(colors.far.copy(alpha = 0f), colors.far.copy(alpha = 0.34f)),
            start = Offset(0f, hazeTop),
            end = Offset(0f, hazeTop + hazeHeight),
        ),
        topLeft = Offset(0f, hazeTop),
        size = Size(w, hazeHeight),
    )

    // Distant ridge — hazier and higher, sitting behind the main range for a sense of depth.
    val ridge = Path().apply {
        moveTo(0f, top + band * 0.30f)
        lineTo(w * 0.22f, top + band * 0.12f)
        lineTo(w * 0.40f, top + band * 0.26f)
        lineTo(w * 0.58f, top + band * 0.06f)
        lineTo(w * 0.78f, top + band * 0.24f)
        lineTo(w, top + band * 0.14f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(ridge, color = colors.far.copy(alpha = 0.55f))

    // Far mountain range.
    val mountain = Path().apply {
        moveTo(0f, top + band * 0.50f)
        lineTo(w * 0.17f, top + band * 0.16f)
        lineTo(w * 0.30f, top + band * 0.46f)
        lineTo(w * 0.48f, top + band * 0.02f)
        lineTo(w * 0.66f, top + band * 0.44f)
        lineTo(w * 0.84f, top + band * 0.18f)
        lineTo(w, top + band * 0.46f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(mountain, color = colors.far.copy(alpha = 0.92f))

    // Near hills.
    val hills = Path().apply {
        moveTo(0f, top + band * 0.66f)
        quadraticTo(w * 0.25f, top + band * 0.46f, w * 0.5f, top + band * 0.60f)
        quadraticTo(w * 0.75f, top + band * 0.76f, w, top + band * 0.58f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(hills, color = colors.near)

    // A river/ribbon that catches the sun's colour.
    val ry = top + band * 0.80f
    val river = Path().apply {
        moveTo(0f, ry)
        quadraticTo(w * 0.25f, ry - band * 0.045f, w * 0.5f, ry + band * 0.055f)
        quadraticTo(w * 0.75f, ry + band * 0.11f, w, ry + band * 0.02f)
        lineTo(w, ry + band * 0.15f)
        quadraticTo(w * 0.75f, ry + band * 0.24f, w * 0.5f, ry + band * 0.19f)
        quadraticTo(w * 0.25f, ry + band * 0.09f, 0f, ry + band * 0.13f)
        close()
    }
    drawPath(river, color = colors.water)

    // Trees, with ground shadows that lean away from the sun. A larger one on the right, a smaller one on
    // the left, add depth without cluttering the lower cards.
    val shadowDir = 0.5f - sunX            // sun on the left → +, shadow falls right
    drawTree(tx = w * 0.80f, groundY = top + band * 0.68f, foliageR = band * 0.20f, shadowDir, colors)
    drawTree(tx = w * 0.19f, groundY = top + band * 0.74f, foliageR = band * 0.13f, shadowDir, colors)
}

private fun DrawScope.drawTree(
    tx: Float,
    groundY: Float,
    foliageR: Float,
    shadowDir: Float,
    colors: SceneColors,
) {
    val shadowLen = min(max(kotlin.math.abs(shadowDir) * 2.4f, 0.35f), 1.4f)
    drawOval(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(tx - foliageR * 1.3f + shadowDir * foliageR * 3.0f, groundY + foliageR * 0.7f),
        size = Size(foliageR * 2.6f * shadowLen, foliageR * 0.7f),
    )

    drawRoundRect(
        color = colors.trunk,
        topLeft = Offset(tx - foliageR * 0.14f, groundY - foliageR * 0.2f),
        size = Size(foliageR * 0.28f, foliageR * 1.1f),
        cornerRadius = CornerRadius(foliageR * 0.1f, foliageR * 0.1f),
    )

    // Three overlapping foliage blobs: a large crown and two lower side puffs.
    val puffs = listOf(Triple(0.0f, -0.9f, 1.0f), Triple(-0.7f, -0.35f, 0.7f), Triple(0.7f, -0.35f, 0.7f))
    for ((dx, dy, r) in puffs) {
        drawCircle(
            color = colors.tree,
            radius = foliageR * r,
            center = Offset(tx + dx * foliageR, groundY + dy * foliageR),
        )
    }
}

/**
 * Static precipitation over the sky: slanted rain streaks (denser for a storm) or round snow flecks, in a
 * cool near-white so they read against both a day and a night base.
 */
private fun DrawScope.drawPrecip(kind: Precip) {
    val w = size.width
    val h = size.height
    when (kind) {
        Precip.SNOW -> {
            val flake = Color.White.copy(alpha = 0.85f)
            for (i in 0 until 44) {
                val x = frac(i * 73 + 17) * w
                val y = frac(i * 149 + 31) * h
                val r = 1.1f + frac(i * 53 + 5) * 1.5f
                drawCircle(color = flake, radius = r, center = Offset(x, y))
            }
        }
        Precip.RAIN, Precip.STORM -> {
            val tint = Color(0.86f, 0.91f, 0.98f).copy(alpha = 0.5f)
            val count = if (kind == Precip.STORM) 72 else 50
            val len = h * (if (kind == Precip.STORM) 0.075f else 0.06f)
            val dx = len * 0.32f                          // a gentle wind-driven slant
            for (i in 0 until count) {
                val x = frac(i * 61 + 13) * (w + 40f) - 20f
                val y = frac(i * 127 + 7) * (h * 1.1f) - h * 0.05f
                drawLine(
                    color = tint,
                    start = Offset(x, y),
                    end = Offset(x - dx, y + len),
                    strokeWidth = 1.3f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
