package com.mab.aura.core.model

import java.net.URI
import java.time.Instant

/**
 * Where a headline came from. Each case carries its public RSS feed and a short display label.
 *
 * Direct port of `NewsSource` in `NewsFeed.swift`. Swift backs the enum with `String` raw values
 * (`rtve`, `aemet`, `meteored`, `aemetBlog`) for `Codable` persistence; the Kotlin enum uses idiomatic
 * SCREAMING_SNAKE names, so when the news cache lands (Layer C/D) its stored key must map `AEMET_BLOG`
 * ↔ `aemetBlog` rather than relying on `name`. The four feed URLs are kept exact.
 */
enum class NewsSource(val displayName: String, val feedURL: URI, val maxItems: Int?) {
    // RTVE is the daily TV weather bulletin; AEMET is official institutional notices; Meteored
    // (tiempo.com) is meteorologists and journalists posting several times a day; the AEMET blog is
    // divulgación by AEMET staff and collaborating university researchers.
    RTVE("RTVE", URI("https://www.rtve.es/api/tematicas/821/noticias.rss"), maxItems = 3),
    AEMET("AEMET", URI("https://www.aemet.es/es/noticias.rss"), maxItems = null),
    METEORED("Meteored", URI("https://www.tiempo.com/feed/"), maxItems = null),
    AEMET_BLOG("AEMET Blog", URI("https://aemetblog.es/feed/"), maxItems = null);
    // `maxItems`: RTVE publishes ~one bulletin a day but its feed carries ~20 entries; we only want today
    // plus the two prior days, so it's capped to 3. The others post varied divulgación worth surfacing
    // more broadly, so they stay uncapped (null).
}

/**
 * One headline: title, article link, its source, publication date, and an optional image URL.
 *
 * Direct port of `NewsItem` in `NewsFeed.swift`. Swift's `Identifiable`/`Hashable` map to the computed
 * [id] and the data-class `equals`/`hashCode`. Swift is also `Codable` (for the news cache); that lands
 * with the caching layer, so no serializer is wired here yet — [URI]/[Instant] would each need one.
 */
data class NewsItem(
    val title: String,
    val link: URI,
    val source: NewsSource,
    val date: Instant,
    val imageURL: URI? = null,
) {
    /** Swift's `Identifiable` id — the article link. */
    val id: URI get() = link
}
