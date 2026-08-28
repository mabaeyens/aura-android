package com.mab.aura.core.text

import com.mab.aura.core.model.WeatherSnapshot
import com.mab.aura.core.sky.SkyCategory
import com.mab.aura.core.sky.SkyCode
import com.mab.aura.core.wind.Beaufort
import com.mab.aura.core.wind.WindDirection
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The hero's two lines of language, composed **on the device** — no network, no model. [headline] is the
 * human one-liner ("Amanece despejado…"); [dataline] folds Máx/Mín, wind, humidity and rain into a second
 * sentence of prose.
 *
 * Direct port of the `ForecastPhrase` enum in `ForecastPhrase.swift`. Two properties matter and the design
 * guarantees both: every clause is built *from* the snapshot's own data (accurate by construction, no
 * free-text generation), and a seed derived from `(day, location)` picks among many phrasings so the same
 * conditions read differently tomorrow and two towns differ today (deterministic, testable, no stored
 * state).
 *
 * Parity note: the whole point is that the seed reproduces identical output run to run, so the RNG must
 * port bit-for-bit, NOT via any platform hash. Swift avoids its own randomised `Hasher` and rolls a
 * SplitMix64 seeded from an FNV-1a string hash; this port does the same with Kotlin's unsigned [ULong]
 * (which wraps on overflow exactly like Swift's `&*`/`&+` masking operators). Swift's `Date` becomes
 * [Instant]; `Calendar.current` (device time zone) becomes an injectable [ZoneId] defaulting to the
 * system zone, used only for the time-of-day bucket and the night check.
 */
object ForecastPhrase {

    // --- Public API -------------------------------------------------------------------------------------

    /** The qualitative one-liner that sits under the temperature. */
    fun headline(
        snapshot: WeatherSnapshot,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val rng = seededRNG(snapshot, now, salt = 1uL)
        val category = SkyCode.classify(snapshot.currentSky).category
        val night = snapshot.isNight(now, zone)
        val bucket = TimeBucket.of(now.atZone(zone).hour)

        val parts = mutableListOf(skyClause(category, night, bucket, rng))

        // Wind earns a mention once it's actually noticeable (force 3+, "flojo" and up).
        val force = Beaufort.force(snapshot.windSpeed)
        val dir = snapshot.windDirection
        if (force >= 3 && dir != null) {
            parts.add(windClause(force, dir, rng))
        }
        // Rain earns a mention when the sky clause doesn't already carry it (clear/cloudy but a wet-ish %).
        val p = snapshot.currentPrecipProb
        if (category != SkyCategory.RAIN && category != SkyCategory.STORM && p != null && p >= 30) {
            parts.add(rainClause(p, rng))
        }

        return finish(join(parts))
    }

    /** The data-as-prose second line: Máx/Mín, wind, humidity, rain — only the parts that are known. */
    fun dataline(snapshot: WeatherSnapshot, now: Instant = Instant.now()): String {
        val rng = seededRNG(snapshot, now, salt = 2uL)
        // Two structures, chosen by the seed, so the line's shape also varies.
        val rangeFirst = rng.bool()

        var clauses = mutableListOf<String>()

        // Temperature.
        val lo = snapshot.tempMin
        val hi = snapshot.tempMax
        if (lo != null && hi != null) {
            clauses.add(if (rangeFirst) "Entre $lo° y $hi°" else "Máxima de $hi°, mínima de $lo°")
        } else if (hi != null) {
            clauses.add("Máxima de $hi°")
        } else if (lo != null) {
            clauses.add("Mínima de $lo°")
        }

        // Wind + humidity share the middle, joined naturally.
        val mid = mutableListOf<String>()
        val speed = snapshot.windSpeed
        if (speed != null) {
            val force = Beaufort.force(speed)
            val dir = snapshot.windDirection
            if (force <= 1) {
                mid.add("viento en calma")
            } else if (dir != null) {
                val name = Beaufort.scale.getOrNull(force)?.name?.lowercase() ?: "flojo"
                mid.add("viento $name del ${dir.spanishName.lowercase()} a $speed km/h")
            } else {
                mid.add("viento a $speed km/h")
            }
        }
        val h = snapshot.currentHumidity
        if (h != null) {
            mid.add(if (rng.bool()) "humedad del $h%" else "un $h% de humedad")
        }
        // Feels-like earns a mention only when it diverges from the temperature shown above (≥ 3°),
        // otherwise it just echoes the number the hero already displays.
        val feels = snapshot.currentFeelsLike
        val ref = snapshot.heroTemp(now)
        if (feels != null && ref != null && kotlin.math.abs(feels - ref) >= 3) {
            mid.add("sensación de $feels°")
        }
        if (mid.isNotEmpty()) {
            val connector = if (clauses.isEmpty()) "" else if (rangeFirst) ", con " else "; "
            val merged = (clauses.firstOrNull() ?: "") + connector + joinList(mid)
            clauses = (listOf(merged) + clauses.drop(1)).toMutableList()
        }

        var sentence = if (clauses.isEmpty()) "" else finish(clauses.joinToString(". "))

        // Rain as its own closing sentence, so it never gets buried — fired by probability, a meaningful
        // amount, or a storm risk.
        val mm = snapshot.currentPrecipMm
        val storm = snapshot.currentStormProb
        if (snapshot.currentPrecipProb != null || (mm ?: 0.0) >= 0.1 || (storm ?: 0) >= 30) {
            val rain = rainSentence(snapshot.currentPrecipProb, mm, storm, rng)
            if (rain.isNotEmpty()) sentence += (if (sentence.isEmpty()) "" else " ") + rain
        }
        // Snow amount as its own note on snowy days.
        val snow = snapshot.currentSnowMm
        if (snow != null && snow >= 0.1) {
            sentence += (if (sentence.isEmpty()) "" else " ") +
                rng.pick(listOf("Unos ${mmText(snow)} de nieve.", "Con unos ${mmText(snow)} de nieve."))
        }
        return if (sentence.isEmpty()) (snapshot.currentSkyText ?: "") else sentence
    }

    // --- Time of day ------------------------------------------------------------------------------------

    enum class TimeBucket {
        DAWN, MORNING, MIDDAY, AFTERNOON, DUSK, NIGHT;

        companion object {
            fun of(hour: Int): TimeBucket = when (hour) {
                in 5..8 -> DAWN
                in 9..11 -> MORNING
                in 12..14 -> MIDDAY
                in 15..18 -> AFTERNOON
                in 19..21 -> DUSK
                else -> NIGHT
            }
        }
    }

    // --- Clause pools -----------------------------------------------------------------------------------

    /**
     * The sky fragment. Clear and few-cloud skies get dawn/dusk flavour ("amanece", "al caer la tarde");
     * the rest stay plain. Night has its own variants where it reads differently.
     */
    private fun skyClause(category: SkyCategory, night: Boolean, bucket: TimeBucket, rng: SplitMix): String =
        when (category) {
            SkyCategory.CLEAR -> {
                if (night) rng.pick(listOf("Noche despejada", "Cielo raso y estrellado",
                    "Noche clara, sin una nube"))
                else when (bucket) {
                    TimeBucket.DAWN -> rng.pick(listOf("Amanece despejado", "El día abre con el cielo limpio",
                        "Arranca la jornada sin una nube"))
                    TimeBucket.DUSK -> rng.pick(listOf("Atardece despejado",
                        "El cielo aguanta despejado al caer la tarde", "Cielo limpio hacia el ocaso"))
                    else -> rng.pick(listOf("Cielo despejado", "Sol y cielo limpio",
                        "Jornada de cielos despejados", "Apenas una nube en todo el día"))
                }
            }
            SkyCategory.FEW_CLOUDS -> {
                if (night) rng.pick(listOf("Noche con algunas nubes", "Nubes dispersas entre claros"))
                else when (bucket) {
                    TimeBucket.DAWN -> rng.pick(listOf("Amanece con nubes y claros", "Nubes altas al amanecer"))
                    TimeBucket.DUSK -> rng.pick(listOf("Nubes y claros al atardecer",
                        "Algunas nubes hacia el ocaso"))
                    else -> rng.pick(listOf("Nubes y claros", "Cielo poco nuboso",
                        "Intervalos de nubes", "Algunas nubes de paso"))
                }
            }
            SkyCategory.CLOUDS -> rng.pick(listOf("Cielo nuboso", "Nubosidad abundante",
                "El cielo se va cubriendo"))
            SkyCategory.OVERCAST -> rng.pick(listOf("Cielo cubierto", "Cielo encapotado",
                "Gris y cerrado todo el día"))
            SkyCategory.RAIN -> rng.pick(listOf("Lluvia intermitente", "Jornada lluviosa",
                "Cielo cubierto con lluvia", "Lluvia a ratos"))
            SkyCategory.STORM -> rng.pick(listOf("Riesgo de tormenta", "Chubascos y tormenta",
                "Cielo tormentoso"))
            SkyCategory.SNOW -> rng.pick(listOf("Nieve", "Cielo con nevadas", "Jornada de nieve"))
            SkyCategory.FOG -> rng.pick(listOf("Niebla", "Bancos de niebla", "Niebla que resta visibilidad"))
            SkyCategory.UNKNOWN -> rng.pick(listOf("Tiempo variable", "Cielo cambiante"))
        }

    private fun windClause(force: Int, direction: WindDirection, rng: SplitMix): String {
        val dir = direction.spanishName.lowercase()
        return when (force) {
            3 -> rng.pick(listOf("viento flojo del $dir", "flojo del $dir"))
            4 -> rng.pick(listOf("viento moderado del $dir", "brisa del $dir"))
            5 -> rng.pick(listOf("viento fresco del $dir", "sopla fresco del $dir"))
            6, 7 -> rng.pick(listOf("viento fuerte del $dir", "rachas fuertes del $dir"))
            else -> rng.pick(listOf("viento muy fuerte del $dir", "temporal de viento del $dir"))
        }
    }

    private fun rainClause(prob: Int, rng: SplitMix): String = when {
        prob >= 70 -> rng.pick(listOf("con lluvia muy probable", "y alta probabilidad de lluvia"))
        prob >= 40 -> rng.pick(listOf("con probabilidad de lluvia", "con chubascos posibles"))
        else -> rng.pick(listOf("con algo de lluvia posible", "sin descartar alguna lluvia"))
    }

    /**
     * The closing rain sentence: probability first (the primary signal), then the mm amount when it's
     * meaningful (≥ 0.1 mm; a trace or 0 is left unsaid), then a storm note when that's likely. Every part
     * is optional, so a dry day still reads cleanly.
     */
    private fun rainSentence(prob: Int?, mm: Double?, storm: Int?, rng: SplitMix): String {
        val amount: String? = if ((mm ?: 0.0) >= 0.1) mmText(mm!!) else null
        val stormNote: String? = if ((storm ?: 0) >= 30)
            rng.pick(listOf("Con riesgo de tormenta ($storm%).", "Posible tormenta ($storm%).")) else null
        fun withStorm(s: String): String = stormNote?.let { if (s.isEmpty()) it else "$s $it" } ?: s

        if (prob == null) {
            val s = amount?.let { rng.pick(listOf("Se esperan unos $it.", "Acumulará en torno a $it.")) } ?: ""
            return withStorm(s)
        }
        if (prob <= 0) {
            if (amount != null) return withStorm(rng.pick(listOf("Apenas $amount.", "Solo unos $amount.")))
            return withStorm(rng.pick(listOf("Sin lluvia.", "No se espera lluvia.", "Jornada seca.")))
        }
        var base = if (prob >= 70)
            rng.pick(listOf("La lluvia es probable: un $prob%", "Alta probabilidad de lluvia, del $prob%"))
        else
            rng.pick(listOf("Probabilidad de lluvia del $prob%", "Un $prob% de probabilidad de lluvia"))
        if (amount != null) base += rng.pick(listOf(", unos $amount", ", con unos $amount"))
        return withStorm("$base.")
    }

    /**
     * A rain/snow amount in Spanish: whole numbers plain ("2 mm"), otherwise one decimal with a comma
     * ("0,4 mm").
     */
    private fun mmText(mm: Double): String {
        // Amounts are non-negative, so `floor(x + 0.5)` reproduces Swift `.rounded()` (ties away from zero).
        val r = kotlin.math.floor(mm * 10 + 0.5) / 10
        if (r % 1.0 == 0.0) return "${r.toInt()} mm"
        return String.format(Locale.US, "%.1f", r).replace('.', ',') + " mm"
    }

    // --- Assembly helpers -------------------------------------------------------------------------------

    /** Join sky/wind/rain fragments with "; " between the first two and ", " before a trailing rain note. */
    private fun join(parts: List<String>): String {
        var out = parts.firstOrNull() ?: return ""
        for ((i, p) in parts.drop(1).withIndex()) {
            out += (if (i == 0) "; " else ", ") + p
        }
        return out
    }

    /** "a, b y c" — Spanish list with "y" before the last item. */
    private fun joinList(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
    }

    /** Capitalise the first letter and guarantee a closing period. */
    private fun finish(s: String): String {
        val first = s.firstOrNull() ?: return s
        val capped = first.uppercase() + s.drop(1)
        return if (capped.endsWith(".")) capped else "$capped."
    }

    // --- Seeded RNG -------------------------------------------------------------------------------------

    /**
     * A tiny deterministic generator (SplitMix64) so phrasing varies by day and place but stays
     * reproducible in tests. [ULong] arithmetic wraps on overflow, matching Swift's `&+`/`&*`/`>>`.
     */
    class SplitMix(seed: ULong) {
        private var state: ULong = seed

        fun next(): ULong {
            state += 0x9E3779B97F4A7C15uL
            var z = state
            z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
            z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
            return z xor (z shr 31)
        }

        fun <T> pick(options: List<T>): T = options[(next() % options.size.toULong()).toInt()]
        fun bool(): Boolean = next() and 1uL == 0uL
    }

    private fun seededRNG(snapshot: WeatherSnapshot, now: Instant, salt: ULong): SplitMix {
        // Day granularity + location, so the same weather reads the same all day but differently tomorrow.
        // Swift's `now.timeIntervalSinceReferenceDate` counts seconds from 2001-01-01 UTC; 978_307_200 is
        // that reference in Unix epoch seconds. Int()/toInt() both truncate toward zero.
        val secsSinceRef = (now.epochSecond - 978_307_200L).toDouble() + now.nano / 1_000_000_000.0
        val day = (secsSinceRef / 86_400.0).toInt()
        var h = fnv1a(snapshot.localidad + "|" + (snapshot.currentSky ?: ""))
        h = h * 1_099_511_628_211uL + day.toLong().toULong()
        return SplitMix(h + salt * 0x100000001B3uL)
    }

    /** A stable FNV-1a string hash (Swift's `Hasher` is randomised per run, breaking reproducibility). */
    private fun fnv1a(s: String): ULong {
        var h = 1_469_598_103_934_665_603uL
        for (b in s.toByteArray(Charsets.UTF_8)) h = (h xor (b.toInt() and 0xFF).toULong()) * 1_099_511_628_211uL
        return h
    }
}
