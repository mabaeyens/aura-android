package com.mab.aura.core.net

import com.mab.aura.core.model.NewsItem
import com.mab.aura.core.model.NewsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the "Noticias" stream — the public weather RSS feeds in [NewsSource] (RTVE, AEMET, Meteored,
 * AEMET Blog) — fetched in parallel, parsed by [NewsFeed], and merged into one recency-sorted list.
 *
 * Android port of `NewsService` in `NewsService.swift`. These are plain public RSS feeds on other hosts, so
 * they carry no AEMET key and don't count against AEMET's request budget — the fetch stays separate from the
 * forecast refresh. Modelled as a class holding an [OkHttpClient], mirroring [OpenMeteoUV]/[MitecoAirQuality]
 * (an injectable client for tests); Swift's `withTaskGroup` becomes `async`/`awaitAll`.
 *
 * One deliberate divergence from the Swift: no disk cache. iOS caches the merged JSON with a 30-min TTL, but
 * that needs a `Codable` [NewsItem]; the Android model defers its URI/Instant serializer (see `News.kt`), so
 * v1 just refetches on load and holds the result in the screen's state for the session. Like the Swift, it
 * never throws — any per-feed failure (bad status, network, malformed XML) drops that source and the rest
 * still show; an all-fail run yields an empty list and the news card simply doesn't appear.
 */
class NewsService(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    companion object {
        /** The merged stream is capped here, matching iOS; [NewsFeed.merge] fills it round-robin by source. */
        const val LIMIT = 20
    }

    /** The merged, recency-sorted headline stream, or `[]` if every feed failed. */
    suspend fun latest(): List<NewsItem> = coroutineScope {
        val groups = NewsSource.values().map { source ->
            async { fetch(source) }
        }.awaitAll()
        NewsFeed.merge(groups, limit = LIMIT)
    }

    /** One source's parsed items, or `[]` on any failure (non-200, network, malformed feed). */
    private suspend fun fetch(source: NewsSource): List<NewsItem> = try {
        // OkHttp's execute() is blocking, so it runs on the IO dispatcher; the body must be read inside use {}
        // before the response closes. Feeds are served in their own encoding (AEMET is ISO-8859-15) — read the
        // raw bytes and let NewsFeed's DOM parser honour the XML prolog's encoding, not string() with UTF-8.
        val bytes = withContext(Dispatchers.IO) {
            httpClient.newCall(Request.Builder().url(source.feedURL.toString()).build()).execute().use { response ->
                if (response.code == 200) response.body?.bytes() else null
            }
        } ?: return emptyList()
        NewsFeed.parse(bytes, source)
    } catch (_: Exception) {
        emptyList()
    }
}
