package com.mab.aura.core.model

import com.mab.aura.core.air.AirQuality
import com.mab.aura.core.solar.SolarTimes
import com.mab.aura.core.uv.UVIndex
import com.mab.aura.core.wind.WindDirection
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/*
 * The `WeatherSnapshot.make()` factory and its private AEMET-mapping helpers — the last of the fetch side.
 * Direct port of the `public extension WeatherSnapshot` in `WeatherSnapshot.swift`. Swift models these as
 * `static` methods in an extension; Kotlin puts them here as extensions on the (empty) `WeatherSnapshot`
 * companion, so call sites read the same (`WeatherSnapshot.make(...)`, `WeatherSnapshot.slots(...)`).
 *
 * No IO happens here: make() maps already-fetched values (Location + the two forecast products, plus the
 * optional observed/alert/air-quality/UV/bulletin inputs) into a snapshot. Swift's `Calendar` fixed to a
 * `TimeZone` becomes an injectable [ZoneId]; `Date` becomes [Instant]. AEMET stamps its hours in the
 * location's civil time ([zone]), so the current hour and the hourly strip are resolved in that zone.
 */

/**
 * Build a snapshot for [location] from the daily forecast (min/max, multi-day list, sun times) and, when
 * available, the hourly forecast (the "now" reading and the hourly strip). [zone] is the location's civil
 * time, in which AEMET stamps its hours.
 */
fun WeatherSnapshot.Companion.make(
    location: Location,
    daily: MunicipioForecast,
    hourly: MunicipioHourly?,
    observed: StationObservation? = null,
    previousObserved: WeatherSnapshot? = null,
    alert: WeatherAlert? = null,
    airQuality: AirQuality? = null,
    uvIndex: UVIndex? = null,
    uvHourly: List<UVHourSlot>? = null,
    bulletin: ForecastBulletin? = null,
    zone: ZoneId = ZoneId.of("Europe/Madrid"),
    now: Instant = Instant.now(),
): WeatherSnapshot {
    val today = daily.prediccion.dia.firstOrNull()
    val sun = SolarTimes.compute(now, location.latitude, location.longitude)

    // Resolve the hourly feed first, so today's daily row and the current humidity can follow the actual
    // current hour rather than a fixed whole-day block.
    val wind = hourly?.let { currentWind(it, zone, now) }
    val humidityNow = hourly?.let { currentHumidity(it, zone, now) }
    val precipNow = hourly?.let { currentPrecipProb(it, zone, now) }
    val precipMmNow = hourly?.let { currentPrecipMm(it, zone, now) }
    val snowMmNow = hourly?.let { currentSnowMm(it, zone, now) }
    val feelsNow = hourly?.let { currentFeelsLike(it, zone, now) }
    val stormNow = hourly?.let { currentStormProb(it, zone, now) }
    val resolved = hourly?.let { resolveHourly(it, zone, now) }

    // Hourly carry-forward: when the hourly feed is momentarily unavailable (the fetch failed or returned
    // nothing, leaving [hourly] null), hold the last good current-hour reading from the prior snapshot rather
    // than blanking every current* field — which would silently drop the hero to today's daily max and default
    // the sky to a bare sun. Mirrors the observation carry-forward below; gated strictly on a wholly-absent
    // feed so a fresh current hour never mixes with a stale one.
    val carry: WeatherSnapshot? = if (hourly == null) previousObserved else null
    val currentSky = resolved?.current?.sky ?: carry?.currentSky

    // Hero temperature: the first upcoming hour that actually carries a reading, not simply the first
    // upcoming slot. AEMET's rolling tail can list a sky for an hour with no matching temperature, so taking
    // that hour's (absent) temp blanked the hero to "--" even though the next hour has one.
    val heroTemp = resolved?.strip?.firstOrNull { it.temp != null }?.temp

    val days = daily.prediccion.dia.take(7).mapIndexedNotNull { idx, dia ->
        val date = parseDay(dia.fecha) ?: return@mapIndexedNotNull null
        // Today (idx 0) follows the current hour: a clear morning shows a sun even when the afternoon turns
        // rainy, and the icon re-adapts as a fresh forecast arrives. Later days keep their daytime summary.
        val sky = if (idx == 0) (currentSky ?: dailySky(dia)) else dailySky(dia)
        DaySnapshot(
            date = date, min = dia.temperatura?.minima, max = dia.temperatura?.maxima,
            humidityMax = dia.humedadRelativa?.maxima,
            sky = sky, windSpeed = dailyWind(dia), probPrecip = dailyPrecip(dia),
        )
    }

    // Observation carry-forward: when this refresh skipped the hourly observation fetch (the feed isn't due
    // yet, or a transient error left [observed] null), keep the last good station reading from the prior
    // snapshot rather than blanking the observed card. All-or-nothing per station, so a fresh reading's
    // fields never mix with a stale one's. Ported from WeatherSnapshot.swift's `previousObserved` path.
    val obsTemp: Int?
    val obsStation: String?
    val obsDistance: Double?
    val obsMetrics: ObservedMetrics
    val obsReading: ObservedReading?
    if (observed != null) {
        obsTemp = observed.temperature
        obsStation = observed.stationName
        obsDistance = observed.distanceKm(to = location)
        obsMetrics = observed.availableMetrics
        obsReading = observed.reading
    } else {
        obsTemp = previousObserved?.observedTemp
        obsStation = previousObserved?.observedStation
        obsDistance = previousObserved?.observedStationDistanceKm
        obsMetrics = previousObserved?.observedMetrics ?: ObservedMetrics()
        obsReading = previousObserved?.observedReading
    }

    return WeatherSnapshot(
        ine = location.ine,
        localidad = location.nombre,
        provincia = location.provincia,
        tempMin = today?.temperatura?.minima,
        tempMax = today?.temperatura?.maxima,
        humedadMax = today?.humedadRelativa?.maxima,
        currentTemp = heroTemp ?: carry?.currentTemp,
        observedTemp = obsTemp,
        observedStation = obsStation,
        observedStationDistanceKm = obsDistance,
        observedMetrics = obsMetrics,
        observedReading = obsReading,
        currentSky = currentSky,
        currentSkyText = resolved?.currentText ?: carry?.currentSkyText,
        currentHumidity = humidityNow ?: carry?.currentHumidity,
        currentPrecipProb = precipNow ?: carry?.currentPrecipProb,
        currentPrecipMm = precipMmNow ?: carry?.currentPrecipMm,
        currentSnowMm = snowMmNow ?: carry?.currentSnowMm,
        currentFeelsLike = feelsNow ?: carry?.currentFeelsLike,
        currentStormProb = stormNow ?: carry?.currentStormProb,
        windSpeed = wind?.speed ?: carry?.windSpeed,
        windDirection = wind?.direction ?: carry?.windDirection,
        windGust = wind?.gust ?: carry?.windGust,
        airQuality = airQuality,
        uvIndex = uvIndex,
        uvHourly = uvHourly,
        sunrise = sun.sunrise,
        sunset = sun.sunset,
        latitude = location.latitude,
        longitude = location.longitude,
        days = days,
        // Carry the last good hourly strip forward when this refresh had no hourly feed, exactly as the
        // current-conditions scalars above carry via `carry`. Without this a transient hourly miss emptied
        // the strip, which blanked the hourly card and, now that the hero resolves from the strip, removed
        // the very data heroTemp(now) re-anchors from. The carried slots keep their absolute timestamps, so
        // upcomingHours(now) still re-anchors them correctly until the next good fetch replaces them.
        hours = resolved?.strip ?: carry?.hours ?: emptyList(),
        alert = alert,
        bulletin = bulletin?.texto,
        bulletinPhenomenon = bulletin?.fenomenoSignificativo,
        updated = now,
    )
}

/**
 * Merge one day's parallel hourly arrays into ordered [HourSlot]s, each stamped with the absolute instant it
 * begins (in [zone]) so the strip can be re-anchored to the current hour at display. Public because
 * `WindGustTests` pins the per-hour wind/gust extraction through it.
 */
fun WeatherSnapshot.Companion.slots(dia: MunicipioHourly.Dia, zone: ZoneId): List<HourSlot> = buildSlots(dia, zone)

/**
 * Parse an AEMET precipitation/snow amount string into mm. "Ip" (precipitación inapreciable) is a trace and
 * reads as 0; empty or non-numeric returns null (treated as "no data"). Accepts a decimal comma or dot.
 */
fun WeatherSnapshot.Companion.precipAmount(raw: String): Double? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    if (t.equals("Ip", ignoreCase = true)) return 0.0
    return t.replace(',', '.').toDoubleOrNull()
}

// --- Hourly resolution ----------------------------------------------------------------------------------

/** The current hour and next few hours, resolved from the hourly forecast. */
private data class ResolvedHourly(val current: HourSlot?, val currentText: String?, val strip: List<HourSlot>)

private fun resolveHourly(forecast: MunicipioHourly, zone: ZoneId, now: Instant): ResolvedHourly {
    val curHour = currentHour(now, zone)
    val dias = futureDays(forecast, zone, now)
    val day0 = dias.firstOrNull()?.let { buildSlots(it, zone).filter { s -> s.hour >= curHour } } ?: emptyList()
    val day1 = if (dias.size > 1) buildSlots(dias[1], zone) else emptyList()
    val upcoming = day0 + day1

    val current = upcoming.firstOrNull()
    // Description for the current hour, read from the *same* day the current slot came from. Once day 0's
    // hours are all past, `current` is day 1's first hour, so its text must come from day 1 too — reading
    // day 0's same-numbered hour would describe a different day and can disagree with the sky code (the
    // "Nubes altas" text over a clear background). null here is honest; a wrong-day text is not.
    val currentDia = if (day0.isEmpty()) dias.getOrNull(1) else dias.firstOrNull()
    val text = currentDia?.let { skyText(it, current?.hour) }

    // Keep a full day ahead so the hourly strip has real data to scroll through (five show at once).
    return ResolvedHourly(current, text, upcoming.take(24))
}

private fun buildSlots(dia: MunicipioHourly.Dia, zone: ZoneId): List<HourSlot> {
    val temps = firstWins(dia.temperatura.mapNotNull { hv -> intKey(hv.periodo)?.let { h -> hv.value.toIntOrNull()?.let { h to it } } })
    val skies = firstWins(dia.estadoCielo.mapNotNull { s -> intKey(s.periodo)?.let { it to s.value } })
    val blocks = parseBlocks(dia.probPrecipitacion)

    // Per-hour wind speed (entries carrying `velocidad`) and peak gust (entries carrying a scalar `value`),
    // both from the mixed vientoAndRachaMax array.
    val winds = firstWins((dia.vientoAndRachaMax ?: emptyList()).mapNotNull { w ->
        val h = intKey(w.periodo) ?: return@mapNotNull null
        val v = w.velocidad?.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
        h to v
    })
    val gusts = firstWins((dia.vientoAndRachaMax ?: emptyList()).mapNotNull { w ->
        val raw = w.value ?: return@mapNotNull null
        if (w.velocidad != null) return@mapNotNull null
        val h = intKey(w.periodo) ?: return@mapNotNull null
        val g = raw.toIntOrNull() ?: return@mapNotNull null
        h to g
    })

    val dayStart = dayMidnight(dia.fecha, zone)
    val hours = (temps.keys + skies.keys).sorted()
    return hours.map { hour ->
        val prob = blocks.firstOrNull { hour in it.start until it.end }?.value
        val date = dayStart?.plusHours(hour.toLong())?.toInstant()
        HourSlot(
            hour = hour, temp = temps[hour], sky = skies[hour], precipProb = prob,
            windSpeed = winds[hour], windGust = gusts[hour], date = date,
        )
    }
}

private fun skyText(dia: MunicipioHourly.Dia, hour: Int?): String? {
    if (hour == null) return null
    return dia.estadoCielo.firstOrNull { intKey(it.periodo) == hour }?.descripcion
}

// --- Current-hour readers -------------------------------------------------------------------------------

/** Speed (km/h), direction, and peak gust for the current hour, or the next available reading. */
private data class CurrentWind(val speed: Int?, val direction: WindDirection?, val gust: Int?)

private fun currentWind(forecast: MunicipioHourly, zone: ZoneId, now: Instant): CurrentWind {
    val from = currentHour(now, zone)

    // Wind entries (velocidad+direccion) at/after `at`, else the day's earliest, as (speed, direction).
    fun wind(dia: MunicipioHourly.Dia, at: Int): Pair<Int?, WindDirection?>? {
        val readings = (dia.vientoAndRachaMax ?: emptyList()).mapNotNull { w ->
            val hour = intKey(w.periodo) ?: return@mapNotNull null
            val speed = w.velocidad?.firstOrNull() ?: return@mapNotNull null
            val dir = w.direccion?.firstOrNull() ?: return@mapNotNull null
            Triple(hour, speed.toIntOrNull(), WindDirection.fromAemet(dir))
        }.sortedBy { it.first }
        val match = readings.firstOrNull { it.first >= at } ?: readings.firstOrNull() ?: return null
        return match.second to match.third
    }

    // Gust entries carry a scalar `value` and no velocidad. Same at/after-`at` preference.
    fun gust(dia: MunicipioHourly.Dia, at: Int): Int? {
        val readings = (dia.vientoAndRachaMax ?: emptyList()).mapNotNull { w ->
            val raw = w.value ?: return@mapNotNull null
            if (w.velocidad != null) return@mapNotNull null
            val hour = intKey(w.periodo) ?: return@mapNotNull null
            val g = raw.toIntOrNull() ?: return@mapNotNull null
            hour to g
        }.sortedBy { it.first }
        return (readings.firstOrNull { it.first >= at } ?: readings.firstOrNull())?.second
    }

    val dias = futureDays(forecast, zone, now)
    dias.firstOrNull()?.let { day0 -> wind(day0, from)?.let { return CurrentWind(it.first, it.second, gust(day0, from)) } }
    if (dias.size > 1) wind(dias[1], 0)?.let { return CurrentWind(it.first, it.second, gust(dias[1], 0)) }
    return CurrentWind(null, null, null)
}

/** The relative humidity for the current hour (or the next available reading), %. */
private fun currentHumidity(forecast: MunicipioHourly, zone: ZoneId, now: Instant): Int? =
    currentSingleHour(forecast, zone, now) { it.humedadRelativa }

/** The feels-like temperature for the current hour, °C. */
private fun currentFeelsLike(forecast: MunicipioHourly, zone: ZoneId, now: Instant): Int? =
    currentSingleHour(forecast, zone, now) { it.sensTermica ?: emptyList() }

/** Current-hour Int reading from a single-hour array (humidity/feels-like keying), else the next available. */
private fun currentSingleHour(
    forecast: MunicipioHourly, zone: ZoneId, now: Instant,
    array: (MunicipioHourly.Dia) -> List<MunicipioHourly.HourValue>,
): Int? {
    val from = currentHour(now, zone)
    fun inDia(dia: MunicipioHourly.Dia, at: Int): Int? =
        pickCurrentOrNext(array(dia).mapNotNull { hv -> intKey(hv.periodo)?.let { h -> hv.value.toIntOrNull()?.let { h to it } } }, at)
    val dias = futureDays(forecast, zone, now)
    dias.firstOrNull()?.let { inDia(it, from)?.let { v -> return v } }
    if (dias.size > 1) inDia(dias[1], 0)?.let { return it }
    return null
}

/** The rain amount for the current hour, mm. */
private fun currentPrecipMm(forecast: MunicipioHourly, zone: ZoneId, now: Instant): Double? =
    currentMm(forecast, zone, now) { it.precipitacion }

/** The snow amount for the current hour, mm. */
private fun currentSnowMm(forecast: MunicipioHourly, zone: ZoneId, now: Instant): Double? =
    currentMm(forecast, zone, now) { it.nieve }

/** Current-hour amount (mm) from one of the hourly amount arrays (single-hour periodo), else the next. */
private fun currentMm(
    forecast: MunicipioHourly, zone: ZoneId, now: Instant,
    array: (MunicipioHourly.Dia) -> List<MunicipioHourly.HourValue>?,
): Double? {
    val from = currentHour(now, zone)
    fun inDia(dia: MunicipioHourly.Dia, at: Int): Double? =
        pickCurrentOrNext((array(dia) ?: emptyList()).mapNotNull { hv -> intKey(hv.periodo)?.let { h -> WeatherSnapshot.precipAmount(hv.value)?.let { h to it } } }, at)
    val dias = futureDays(forecast, zone, now)
    dias.firstOrNull()?.let { inDia(it, from)?.let { v -> return v } }
    if (dias.size > 1) inDia(dias[1], 0)?.let { return it }
    return null
}

/** The precipitation probability for the current hour, %, from the coarse "SSEE" blocks. */
private fun currentPrecipProb(forecast: MunicipioHourly, zone: ZoneId, now: Instant): Int? =
    currentBlockProb(forecast, zone, now) { it.probPrecipitacion }

/** The storm probability for the current hour, %, from the same coarse-block format. */
private fun currentStormProb(forecast: MunicipioHourly, zone: ZoneId, now: Instant): Int? =
    currentBlockProb(forecast, zone, now) { it.probTormenta ?: emptyList() }

private fun currentBlockProb(
    forecast: MunicipioHourly, zone: ZoneId, now: Instant,
    array: (MunicipioHourly.Dia) -> List<MunicipioHourly.HourValue>,
): Int? {
    val from = currentHour(now, zone)
    fun inDia(dia: MunicipioHourly.Dia, at: Int): Int? {
        val blocks = parseBlocks(array(dia))
        blocks.firstOrNull { at in it.start until it.end }?.let { return it.value }
        blocks.firstOrNull { it.start >= at }?.let { return it.value }
        return blocks.firstOrNull()?.value
    }
    val dias = futureDays(forecast, zone, now)
    dias.firstOrNull()?.let { inDia(it, from)?.let { v -> return v } }
    if (dias.size > 1) inDia(dias[1], 0)?.let { return it }
    return null
}

// --- Daily-block readers --------------------------------------------------------------------------------

// AEMET's coarse daily blocks, in the preference order that favours a daytime summary (so the days card
// shows a sun rather than a moon).
private val DAY_BLOCK_ORDER = listOf("00-24", "12-24", "12", "06-12", "00-12")

/** The daytime sky code for a daily forecast block. */
private fun dailySky(dia: MunicipioForecast.Dia): String? {
    val blocks = dia.estadoCielo ?: emptyList()
    for (periodo in DAY_BLOCK_ORDER) {
        blocks.firstOrNull { it.periodo == periodo }?.value?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return blocks.firstOrNull { it.value.isNotEmpty() }?.value
}

/** The representative wind speed for a daily forecast block, km/h. */
private fun dailyWind(dia: MunicipioForecast.Dia): Int? {
    val blocks = dia.viento ?: emptyList()
    for (periodo in DAY_BLOCK_ORDER) {
        blocks.firstOrNull { it.periodo == periodo }?.velocidad?.let { return it }
    }
    return blocks.mapNotNull { it.velocidad }.maxOrNull()
}

/** The representative precipitation probability for a day, % — the max across AEMET's coarse blocks. */
private fun dailyPrecip(dia: MunicipioForecast.Dia): Int? =
    (dia.probPrecipitacion ?: emptyList()).mapNotNull { it.value }.maxOrNull()

// --- Small shared helpers -------------------------------------------------------------------------------

/**
 * AEMET's hourly feed can briefly lead with a stale *past* day: for part of the morning its first `dia` is
 * still yesterday, carrying only a handful of tail hours. Every current-hour reader assumes `dia[0]` is
 * today and filters by bare hour-of-day, so a yesterday-evening hour (e.g. 20:00) whose number is still >=
 * the current morning hour survives the filter and is read as "now" — pinning the hero to a slot that can
 * carry a sky but no temperature, which blanked it to "--". Drop any day before the current calendar day so
 * resolution always anchors on today; fall back to the raw list if that would leave nothing (a wholly stale
 * feed), so behaviour is never worse than before.
 */
private fun futureDays(forecast: MunicipioHourly, zone: ZoneId, now: Instant): List<MunicipioHourly.Dia> {
    val today = now.atZone(zone).toLocalDate()
    val kept = forecast.prediccion.dia.dropWhile { dia ->
        val date = parseLocalDate(dia.fecha) ?: return@dropWhile false
        date < today
    }
    return kept.ifEmpty { forecast.prediccion.dia }
}

private data class Block(val start: Int, val end: Int, val value: Int)

/** Parse the coarse "SSEE" precipitation/storm blocks (e.g. "1218"); an end of "00" wraps to 24. */
private fun parseBlocks(items: List<MunicipioHourly.HourValue>): List<Block> =
    items.mapNotNull { hv ->
        if (hv.periodo.length != 4) return@mapNotNull null
        val start = hv.periodo.take(2).toIntOrNull() ?: return@mapNotNull null
        var end = hv.periodo.takeLast(2).toIntOrNull() ?: return@mapNotNull null
        val value = hv.value.toIntOrNull() ?: return@mapNotNull null
        if (end == 0) end = 24
        Block(start, end, value)
    }.sortedBy { it.start }

/** The reading covering `at` (or the next one at/after it, or the earliest), from single-hour readings. */
private fun <T> pickCurrentOrNext(readings: List<Pair<Int, T>>, at: Int): T? {
    val sorted = readings.sortedBy { it.first }
    return (sorted.firstOrNull { it.first >= at } ?: sorted.firstOrNull())?.second
}

/** Build a map keeping the FIRST value per key, matching Swift's `uniquingKeysWith: { a, _ in a }`. */
private fun <V> firstWins(entries: List<Pair<Int, V>>): Map<Int, V> {
    val map = LinkedHashMap<Int, V>()
    for ((k, v) in entries) if (k !in map) map[k] = v
    return map
}

/** Parse an hour/period token to Int; null (dropped) when absent or non-numeric. */
private fun intKey(periodo: String?): Int? = periodo?.toIntOrNull()

/** The current hour-of-day (0–23) in [zone]. */
private fun currentHour(now: Instant, zone: ZoneId): Int = now.atZone(zone).hour

/**
 * Parse AEMET's daily `fecha` ("yyyy-MM-dd" or "…'T'HH:mm:ss") to noon UTC, so day labels are stable
 * regardless of the reader's zone.
 */
private fun parseDay(raw: String): Instant? {
    val date = parseLocalDate(raw) ?: return null
    return date.atTime(12, 0).toInstant(ZoneOffset.UTC)
}

/**
 * Midnight (local, in [zone]) of the calendar day AEMET's hourly `fecha` names — the anchor for each hour's
 * absolute instant. DST is handled by [ZonedDateTime] arithmetic at the call site.
 */
private fun dayMidnight(raw: String, zone: ZoneId): ZonedDateTime? =
    parseLocalDate(raw)?.atStartOfDay(zone)

private fun parseLocalDate(raw: String): LocalDate? =
    try {
        if (raw.contains('T')) LocalDateTime.parse(raw).toLocalDate() else LocalDate.parse(raw)
    } catch (_: Exception) {
        null
    }
