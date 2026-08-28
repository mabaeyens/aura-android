package com.mab.aura.core.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How current a cached snapshot is, measured only from its [WeatherSnapshot.updated] stamp against the render
 * time. The widget is a pure cache reader (it never fetches; see `AuraGlanceWidget`), so a snapshot can be
 * hours or a day old with nothing on the tile to say so. This is the signal a surface uses to draw an honest
 * staleness badge instead of silently presenting an old value as if it were live.
 *
 * Android port of the iOS `SnapshotFreshness` enum in `AuraKit/SnapshotFreshness.swift`. Keep the two aligned:
 * same thresholds, same three cases, same label strings, so the app and widget agree on what "current" means.
 */
enum class SnapshotFreshness {
    /** Within the app's own 1 h stale gate. No badge; treat as current. */
    FRESH,

    /** Older than an hour but still inside the ~24 h strip horizon, where display-time resolution
     *  ([WeatherSnapshot.resolved]) keeps the shown values correct. The badge is informational
     *  ("actualizado HH:mm"), not an error. */
    RECENT,

    /** Past the ~24 h horizon: the hero can no longer re-anchor to today, so the badge escalates to an honest
     *  "Desactualizado". */
    STALE,
}

/** Under an hour old counts as fresh — the same gate the app uses before it refetches
 *  (`WeatherRepository.refresh`), so the app and widget agree on what "current" means. */
val RECENT_THRESHOLD: Duration = Duration.ofHours(1)

/** The next-hours strip spans roughly a day; past it, display-time resolution can no longer show today. */
val STALE_THRESHOLD: Duration = Duration.ofHours(24)

// The label text is core-generated Spanish, exactly like the Beaufort/UV/ICA labels and ForecastPhrase, and
// exactly like the iOS strings — it is not localized to the app's UI language on purpose (see the localization
// scope note). es-ES so a device set to another language still stamps the time the Spanish way.
private val STALENESS_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-ES"))

/**
 * The snapshot's freshness at [now], from its [WeatherSnapshot.updated] stamp. A future stamp (device clock
 * skew) reads as fresh, never stale, because a negative age is below every threshold.
 */
fun WeatherSnapshot.freshness(now: Instant = Instant.now()): SnapshotFreshness {
    val age = Duration.between(updated, now)
    if (age < RECENT_THRESHOLD) return SnapshotFreshness.FRESH
    if (age < STALE_THRESHOLD) return SnapshotFreshness.RECENT
    return SnapshotFreshness.STALE
}

/**
 * The short badge string for a widget surface, or null when fresh enough to need none. [SnapshotFreshness.RECENT]
 * shows when the data was fetched, in the location's own time; [SnapshotFreshness.STALE] states the fact. Pass
 * the location's [zone]; it defaults to Europe/Madrid to match the rest of the snapshot's time handling.
 */
fun WeatherSnapshot.stalenessLabel(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.of("Europe/Madrid"),
): String? = when (freshness(now)) {
    SnapshotFreshness.FRESH -> null
    SnapshotFreshness.RECENT -> "actualizado " + STALENESS_TIME_FORMAT.withZone(zone).format(updated)
    SnapshotFreshness.STALE -> "Desactualizado"
}
