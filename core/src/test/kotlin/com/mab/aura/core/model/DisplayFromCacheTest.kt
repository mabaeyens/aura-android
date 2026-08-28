package com.mab.aura.core.model

import com.mab.aura.core.wind.WindDirection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The release gate for the display-time hero fix, ported from the iOS `DisplayFromCacheTests` (spec
 * `hero-current-temp-structural-fix.md` §4 case (a)). This is the real user scenario that no earlier test
 * covered: a snapshot **built yesterday** and served from the cache today, with no fresh fetch.
 *
 * The bug this pins was structural, not a fetch bug. The hero used to render a scalar frozen when the
 * snapshot was built ([WeatherSnapshot.currentTemp]); a cache built yesterday evening therefore showed
 * yesterday's temperature — or, when that build produced a thin snapshot, "--" — after midnight. The strip
 * on the very same snapshot already carried today's absolutely-timestamped hours; the hero simply wasn't
 * reading them. [WeatherSnapshot.heroTemp] now resolves against the real clock at display time, so the
 * day-old cache shows *today's* number with no network at all — which is also what makes the pure-cache
 * widget self-heal at a day change.
 */
class DisplayFromCacheTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val json = Json { ignoreUnknownKeys = true }

    private val location = Location(ine = "28079", nombre = "Madrid", provincia = "Madrid",
        latitude = 40.4168, longitude = -3.7038)

    // Build time: 22:00 Madrid on the 27th (UTC+2 in August = 20:00 UTC).
    private val builtOn27th = Instant.parse("2026-08-27T20:00:00Z")
    // Display time: 09:00 Madrid on the 28th — a day later, no refetch in between.
    private val openedOn28th = Instant.parse("2026-08-28T07:00:00Z")

    // Daily product for the 28th (the hero must never read its max of 30).
    private fun daily(): MunicipioForecast {
        val payload = """
            [{"nombre":"Madrid","provincia":"Madrid","prediccion":{"dia":[
              {"fecha":"2026-08-28T00:00:00",
               "temperatura":{"maxima":30,"minima":15},
               "humedadRelativa":{"maxima":70,"minima":30}}
            ]}}]
        """.trimIndent()
        return json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MunicipioForecast.serializer()), payload)[0]
    }

    // The hourly feed as it looked when fetched on the evening of the 27th: dia[0] is the 27th's tail
    // (22:00/23:00), dia[1] is the 28th (morning hours, 09:00 = 21°). AEMET's hourly product always carries
    // the next day, which is exactly why yesterday's cache already knows today's hours.
    private fun hourly(): MunicipioHourly {
        val payload = """
            {"nombre":"Madrid","provincia":"Madrid","prediccion":{"dia":[
              {"fecha":"2026-08-27T00:00:00",
               "temperatura":[{"value":"16","periodo":"22"},{"value":"15","periodo":"23"}],
               "estadoCielo":[{"value":"11n","periodo":"22","descripcion":"Despejado"},{"value":"11n","periodo":"23","descripcion":"Despejado"}],
               "humedadRelativa":[{"value":"60","periodo":"22"},{"value":"62","periodo":"23"}],
               "sensTermica":[{"value":"14","periodo":"22"},{"value":"13","periodo":"23"}],
               "probPrecipitacion":[{"value":"0","periodo":"2024"}],
               "vientoAndRachaMax":[{"direccion":["S"],"velocidad":["8"],"periodo":"22"}]},
              {"fecha":"2026-08-28T00:00:00",
               "temperatura":[{"value":"17","periodo":"07"},{"value":"19","periodo":"08"},{"value":"21","periodo":"09"},{"value":"23","periodo":"10"},{"value":"25","periodo":"11"}],
               "estadoCielo":[{"value":"11","periodo":"07","descripcion":"Despejado"},{"value":"11","periodo":"08","descripcion":"Despejado"},{"value":"12","periodo":"09","descripcion":"Poco nuboso"},{"value":"12","periodo":"10","descripcion":"Poco nuboso"},{"value":"12","periodo":"11","descripcion":"Poco nuboso"}],
               "humedadRelativa":[{"value":"55","periodo":"07"},{"value":"52","periodo":"08"},{"value":"48","periodo":"09"},{"value":"45","periodo":"10"},{"value":"42","periodo":"11"}],
               "sensTermica":[{"value":"16","periodo":"07"},{"value":"18","periodo":"08"},{"value":"20","periodo":"09"},{"value":"22","periodo":"10"},{"value":"24","periodo":"11"}],
               "probPrecipitacion":[{"value":"0","periodo":"0612"}],
               "probTormenta":[{"value":"5","periodo":"0612"}],
               "vientoAndRachaMax":[{"direccion":["NE"],"velocidad":["12"],"periodo":"09"},{"direccion":["NE"],"velocidad":["14"],"periodo":"10"}]}
            ]}}
        """.trimIndent()
        return json.decodeFromString(MunicipioHourly.serializer(), payload)
    }

    @Test
    fun dayOldCache_rendersTodaysHeroWithNoFetch() {
        // Snapshot built yesterday evening and cached — this is what the app/widget reads today.
        val cached = WeatherSnapshot.make(location, daily(), hourly(), zone = madrid, now = builtOn27th)

        // The scalar frozen at build time is yesterday's 22:00 reading. Rendering *this* is the old bug:
        // a day later it shows 16°, and it is the value the hero used to display.
        assertEquals(16, cached.currentTemp)

        // The display-time hero, resolved against the real clock, reads the strip's 28th 09:00 slot: 21°.
        val hero = cached.heroTemp(openedOn28th, madrid)
        assertEquals(21, hero)                 // today's number, straight from the day-old cache
        assertNotEquals(cached.currentTemp, hero)  // proves the hero is not the frozen 22:00 scalar
        assertNotEquals(cached.tempMax, hero)      // and not today's daily max (30)
    }

    @Test
    fun dayOldCache_resolvesTheWholeCurrentFamilyToToday() {
        val cached = WeatherSnapshot.make(location, daily(), hourly(), zone = madrid, now = builtOn27th)

        // Every current* scalar is frozen at the 27th 22:00 build hour — the old day-stale bug rendered these.
        assertEquals("11n", cached.currentSky)
        assertEquals("Despejado", cached.currentSkyText)
        assertEquals(60, cached.currentHumidity)
        assertEquals(14, cached.currentFeelsLike)
        assertEquals(8, cached.windSpeed)
        assertEquals(WindDirection.fromAemet("S"), cached.windDirection)

        // resolved() re-derives the whole family from the strip against the real clock: the 28th 09:00 hour.
        val now = cached.resolved(openedOn28th, madrid)
        assertEquals(21, now.currentTemp)                       // matches heroTemp — one mechanism
        assertEquals(now.currentTemp, now.heroTemp(openedOn28th, madrid))
        assertEquals("12", now.currentSky)                      // today's sky, not yesterday's "11n"
        assertEquals("Poco nuboso", now.currentSkyText)
        assertEquals(48, now.currentHumidity)
        assertEquals(20, now.currentFeelsLike)
        assertEquals(12, now.windSpeed)                         // first upcoming hour with wind (09:00)
        assertEquals(WindDirection.fromAemet("NE"), now.windDirection)
        assertEquals(5, now.currentStormProb)                   // from the 06-12 block

        // The hero's sky can no longer disagree with the strip's first column: both are the 09:00 hour.
        assertEquals(cached.upcomingHours(openedOn28th, madrid).first().sky, now.currentSky)
    }

    @Test
    fun resolved_emptyStripReturnsSnapshotUnchanged() {
        // A thin snapshot: daily only, no hourly feed and no prior to carry a strip from.
        val thin = WeatherSnapshot.make(location, daily(), hourly = null, zone = madrid, now = builtOn27th)
        assertEquals(emptyList<HourSlot>(), thin.hours)
        assertEquals(thin, thin.resolved(openedOn28th, madrid))  // nothing to re-derive; frozen scalars stand
    }
}
