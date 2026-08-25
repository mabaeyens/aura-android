package com.mab.aura.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mab.aura.core.model.WeatherAlert
import com.mab.aura.ui.theme.Palette
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * An active AEMET aviso (weather warning). Collapsed it's a one-line glance (icon, phenomenon, chevron);
 * tapped it reveals the aviso's own full text: AEMET's event title, the affected zone, and the validity
 * window. Coloured by warning level via [Palette.alert]. Ported from `AuraAlertCard` in
 * `AuraAppCards.swift`. Unlike the frosted cards this carries its own tinted background, so it doesn't
 * use [AuraCard] and isn't wrapped in a section title.
 */
@Composable
fun AuraAlertCard(
    alert: WeatherAlert,
    size: AuraSize,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(size.cardCorner)
    val level = Palette.alert(alert.level)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Contraer" else "Ver el aviso completo") {
                expanded = !expanded
            }
            .background(level.copy(alpha = 0.5f), shape)
            .border(0.5.dp, level.copy(alpha = 0.7f), shape)
            .padding(size.cardPadding),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size.iconSize),
            )
            Text(
                text = alert.phenomenon ?: alert.event,
                fontSize = size.bodySize - 1,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
            )
        }
        AnimatedVisibility(visible = expanded) {
            // The warning's full text: AEMET's own event title, the affected zone, and the window it's
            // valid for. That is everything the aviso itself carries.
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = alert.event,
                    fontSize = size.bodySize - 2,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                )
                val zone = alert.areaDesc
                if (!zone.isNullOrEmpty()) {
                    Text(
                        text = zone,
                        fontSize = size.bodySize - 3,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                validityText(alert)?.let { validity ->
                    Text(
                        text = validity,
                        fontSize = size.bodySize - 3,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// Abbreviated weekday + 24h time in es-ES, e.g. "mié 18:00". The 12/24h preference moves here once the
// settings store lands (Layer D); until then this follows the Spanish 24h convention.
private val validityFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE HH:mm", Locale("es", "ES")).withZone(ZoneId.systemDefault())

/**
 * The aviso's validity window in plain Spanish, no dashes: "De {inicio} a {fin}", "Hasta {fin}",
 * "Desde {inicio}", or null when AEMET gave no times.
 */
private fun validityText(alert: WeatherAlert): String? {
    val start = alert.onset
    val end = alert.expires
    val f: (java.time.Instant) -> String = { validityFormatter.format(it) }
    return when {
        start != null && end != null -> "De ${f(start)} a ${f(end)}"
        start == null && end != null -> "Hasta ${f(end)}"
        start != null && end == null -> "Desde ${f(start)}"
        else -> null
    }
}
