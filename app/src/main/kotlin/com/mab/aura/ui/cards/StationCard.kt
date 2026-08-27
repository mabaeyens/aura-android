package com.mab.aura.ui.cards

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.aura.R
import com.mab.aura.core.model.ObservedMetrics
import com.mab.aura.core.model.ObservedReading
import com.mab.aura.core.model.WeatherSnapshot
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The nearest recent AEMET station behind this location's observed reading: its name, how far it sits, and
 * which surface metrics it actually reports — so a station that measures only some fields reads as clearly
 * as one that measures them all. Ported from `AuraStationCard` in `AuraAppCards.swift`.
 *
 * Built only when a station resolved (`snapshot.observedStation != null`); the caller drops it otherwise.
 * A chip the station doesn't report is greyed with a dash (MITECO's grey-for-unavailable convention, the
 * same one [AuraAirQualityCard] uses), and a closing line spells out which metrics are missing. The chips
 * are monochrome vectors, matching the iOS SF Symbols — deliberately not the colourful `ic_wx_*` Meteocons,
 * so the five read as one uniform row and dim together.
 */
@Composable
fun AuraStationCard(
    snapshot: WeatherSnapshot,
    size: AuraSize,
    modifier: Modifier = Modifier,
) {
    val available = snapshot.observedMetrics
    val reading = snapshot.observedReading
    val context = LocalContext.current

    AuraSection(stringResource(R.string.card_station_title).uppercase(), size, modifier = modifier) {
        AuraCard(size) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = header(snapshot, context),
                    fontSize = size.bodySize - 1,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    METRICS.forEach { metric ->
                        val on = available.contains(metric.flag)
                        MetricChip(
                            metric = metric,
                            value = if (on) value(metric.flag, reading) else null,
                            on = on,
                            size = size,
                        )
                    }
                }
                Text(
                    text = completeness(available, context),
                    fontSize = size.smallSize - 1,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 2,
                )
            }
        }
    }
}

/** One metric's chip icon, the short on-screen label, and the full name for the completeness line. */
private class Metric(
    val flag: Int,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    @StringRes val full: Int,
)

// The canonical metric order, matching the iOS card. Labels are pre-abbreviated to a common width ("Humed.",
// "Pres.") so every chip's label renders at the same size.
private val METRICS = listOf(
    Metric(ObservedMetrics.TEMPERATURE, R.drawable.ic_metric_temp, R.string.card_station_metric_temperature_label, R.string.card_station_metric_temperature_full),
    Metric(ObservedMetrics.WIND, R.drawable.ic_metric_wind, R.string.card_station_metric_wind_label, R.string.card_station_metric_wind_full),
    Metric(ObservedMetrics.HUMIDITY, R.drawable.ic_metric_humidity, R.string.card_station_metric_humidity_label, R.string.card_station_metric_humidity_full),
    Metric(ObservedMetrics.PRESSURE, R.drawable.ic_metric_pressure, R.string.card_station_metric_pressure_label, R.string.card_station_metric_pressure_full),
    Metric(ObservedMetrics.PRECIPITATION, R.drawable.ic_metric_rain, R.string.card_station_metric_precipitation_label, R.string.card_station_metric_precipitation_full),
)

/**
 * One metric chip: icon over the station's measured value over the short label. Greyed with a dash for the
 * value when the station doesn't report the metric. `weight(1f)` gives all five chips equal width.
 */
@Composable
private fun RowScope.MetricChip(metric: Metric, value: String?, on: Boolean, size: AuraSize) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = if (on) 0.12f else 0.04f))
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painter = painterResource(metric.icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (on) 1f else 0.3f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = value ?: "—",
            fontSize = size.bodySize - 2,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = if (on) 0.95f else 0.35f),
            maxLines = 1,
        )
        Text(
            text = stringResource(metric.label),
            fontSize = size.smallSize - 3,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = if (on) 0.7f else 0.35f),
            maxLines = 1,
        )
    }
}

/**
 * The station's measured value for a metric, formatted for its chip (the unit is implied by the label). Null
 * when the station doesn't report it, so the chip shows a dash. Spanish decimal comma for rain.
 */
private fun value(flag: Int, reading: ObservedReading?): String? {
    if (reading == null) return null
    return when (flag) {
        ObservedMetrics.TEMPERATURE -> reading.temperature?.let { "$it°" }
        ObservedMetrics.WIND -> reading.windKmh?.let { "$it" }
        ObservedMetrics.HUMIDITY -> reading.humidity?.let { "$it%" }
        ObservedMetrics.PRESSURE -> reading.pressure?.let { "$it" }
        ObservedMetrics.PRECIPITATION -> reading.precipMm?.let {
            String.format(Locale.US, "%.1f", it).replace('.', ',')
        }
        else -> null
    }
}

/** "Madrid Retiro · a 3 km" — the station and its distance (Spanish decimal comma under 10 km, whole km beyond). */
private fun header(snapshot: WeatherSnapshot, context: Context): String {
    val name = snapshot.observedStation ?: "—"
    val km = snapshot.observedStationDistanceKm ?: return name
    val dist = if (km < 10) {
        String.format(Locale.US, "%.1f", km).replace('.', ',')
    } else {
        "${km.roundToInt()}"
    }
    return context.getString(R.string.card_station_header, name, dist)
}

/** "Mide todos los datos de superficie." or "No mide: presión y precipitación." */
private fun completeness(available: ObservedMetrics, context: Context): String {
    val missing = METRICS.filter { !available.contains(it.flag) }.map { context.getString(it.full) }
    if (missing.isEmpty()) return context.getString(R.string.card_station_complete)
    val list = if (missing.size > 1) {
        context.getString(
            R.string.card_station_list_conjunction,
            missing.dropLast(1).joinToString(", "),
            missing.last(),
        )
    } else {
        missing[0]
    }
    return context.getString(R.string.card_station_missing, list)
}
