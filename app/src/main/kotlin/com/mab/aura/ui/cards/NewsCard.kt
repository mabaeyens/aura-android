package com.mab.aura.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mab.aura.core.model.NewsItem
import com.mab.aura.core.model.NewsSource
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/**
 * The "Noticias" stream: the most recent official weather headlines (RTVE, AEMET, Meteored, AEMET Blog),
 * already round-robin merged in `:core` so no source dominates. Each row opens the article in the
 * browser. Ported from `AuraNewsCard` in `AuraAppCards.swift`. The card renders only when the stack is
 * given items, so an empty list simply omits it.
 */
@Composable
fun AuraNewsCard(
    items: List<NewsItem>,
    size: AuraSize,
    now: Instant = Instant.now(),
    modifier: Modifier = Modifier,
) {
    AuraSection("NOTICIAS", size) {
        AuraCard(size, modifier) {
            Column {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    NewsRow(item, size, now)
                }
            }
        }
    }
}

@Composable
private fun NewsRow(item: NewsItem, size: AuraSize, now: Instant) {
    val uriHandler = LocalUriHandler.current
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(item.link.toString()) },
    ) {
        Text(
            text = item.title,
            fontSize = size.bodySize - 4,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.source.displayName,
                fontSize = size.smallSize - 3,
                fontWeight = FontWeight.ExtraBold, // Swift `.heavy`
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50)) // Swift Capsule
                    .background(badgeColor(item.source))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(
                text = relative(item.date, now),
                fontSize = size.smallSize - 2,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

/** A distinct badge colour per source, so the mix is scannable at a glance. */
private fun badgeColor(source: NewsSource): Color = when (source) {
    NewsSource.RTVE -> Color(red = 0.00f, green = 0.45f, blue = 0.80f)       // RTVE blue
    NewsSource.AEMET -> Color(red = 0.85f, green = 0.38f, blue = 0.10f)      // AEMET orange
    NewsSource.METEORED -> Color(red = 0.11f, green = 0.60f, blue = 0.51f)   // Meteored teal-green
    NewsSource.AEMET_BLOG -> Color(red = 0.40f, green = 0.42f, blue = 0.80f) // AEMET Blog indigo
}

/** "hace 2 h", "hace 3 d", "hace 40 min", or "ahora" within the last five minutes. */
internal fun relative(date: Instant, now: Instant): String {
    val seconds = max(0L, Duration.between(date, now).seconds)
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 1 -> "hace $days d"
        hours >= 1 -> "hace $hours h"
        minutes >= 5 -> "hace $minutes min"
        else -> "ahora"
    }
}
