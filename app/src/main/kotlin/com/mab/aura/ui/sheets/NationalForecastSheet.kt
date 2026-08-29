package com.mab.aura.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.aura.R
import com.mab.aura.core.model.ForecastBulletin
import com.mab.aura.core.model.MedioPlazoForecast
import com.mab.aura.core.text.BulletinText
import com.mab.aura.ui.cards.NationalForecastState
import com.mab.aura.ui.cards.issuedText
import com.mab.aura.ui.cards.validezText
import java.util.Locale

/**
 * The "Predicción nacional" detail sheet: the four national text products across a segmented control —
 * Hoy / Mañana / Pasado mañana / Medio plazo. Today is already in hand (from [NationalForecastState.today]);
 * the other three fetch **lazily the first time their segment is opened**, via the suspend loaders the state
 * carries, so a sheet that's opened but never paged past "Hoy" makes no extra network calls.
 *
 * Each segment shows a spinner while it loads and a quiet "unavailable" note if the fetch comes back empty; a
 * loaded segment is remembered for the life of the sheet (and cached in the repository), so switching back is
 * instant. The bulletins are AEMET's Spanish text, shown verbatim; only the chrome is localized.
 */
@Composable
internal fun AuraNationalForecastSheet(state: NationalForecastState, onClose: () -> Unit) {
    var selected by remember { mutableStateOf(Segment.HOY) }

    // Deferred segments: null = not yet requested, Loading = fetch in flight, Ready(value) = done (value null
    // means the fetch came back empty). Remembered across segment switches for the life of the sheet.
    var manana by remember { mutableStateOf<Seg<ForecastBulletin>?>(null) }
    var pasado by remember { mutableStateOf<Seg<ForecastBulletin>?>(null) }
    var medio by remember { mutableStateOf<Seg<MedioPlazoForecast>?>(null) }

    // Fetch a deferred segment the first time it's opened. Keyed on the selection, so it only fires on a change;
    // the null-guard makes it a one-shot per segment (a loaded segment is never refetched here).
    LaunchedEffect(selected) {
        when (selected) {
            Segment.HOY -> Unit
            Segment.MANANA -> if (manana == null) { manana = Seg.Loading; manana = Seg.Ready(state.loadManana()) }
            Segment.PASADO -> if (pasado == null) { pasado = Seg.Loading; pasado = Seg.Ready(state.loadPasadoManana()) }
            Segment.MEDIO -> if (medio == null) { medio = Seg.Loading; medio = Seg.Ready(state.loadMedioPlazo()) }
        }
    }

    SheetScaffold(
        gradient = listOf(Color(0.09f, 0.12f, 0.19f), Color(0.03f, 0.04f, 0.08f)),
        onClose = onClose,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.sheet_national_title),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(end = 34.dp), // clear of the close button
                )
                Text(
                    text = stringResource(R.string.sheet_national_subtitle),
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            SegmentBar(selected = selected, onSelect = { selected = it })

            when (selected) {
                Segment.HOY -> BulletinBody(state.today)
                Segment.MANANA -> BulletinSegment(manana)
                Segment.PASADO -> BulletinSegment(pasado)
                Segment.MEDIO -> MedioSegment(medio)
            }
        }
    }
}

/** The four sheet segments, in order. */
private enum class Segment(val labelRes: Int) {
    HOY(R.string.seg_national_hoy),
    MANANA(R.string.seg_national_manana),
    PASADO(R.string.seg_national_pasado),
    MEDIO(R.string.seg_national_medio),
}

/** A deferred segment's load state. [Ready] wraps a nullable value; null means the fetch came back empty. */
private sealed interface Seg<out T> {
    data object Loading : Seg<Nothing>
    data class Ready<T>(val value: T?) : Seg<T>
}

/** The row of selectable pills across the top of the sheet, one per [Segment]. */
@Composable
private fun SegmentBar(selected: Segment, onSelect: (Segment) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Segment.entries.forEach { segment ->
            val isSelected = segment == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                    )
                    .clickable { onSelect(segment) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(segment.labelRes),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = Color.White.copy(alpha = if (isSelected) 0.95f else 0.6f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Render a deferred bulletin segment through its load state. */
@Composable
private fun BulletinSegment(seg: Seg<ForecastBulletin>?) {
    when (seg) {
        null, Seg.Loading -> SegmentSpinner()
        is Seg.Ready -> seg.value?.let { BulletinBody(it) } ?: SegmentUnavailable()
    }
}

/** Render the deferred medium-range segment through its load state. */
@Composable
private fun MedioSegment(seg: Seg<MedioPlazoForecast>?) {
    when (seg) {
        null, Seg.Loading -> SegmentSpinner()
        is Seg.Ready -> seg.value?.let { MedioBody(it) } ?: SegmentUnavailable()
    }
}

/** One bulletin's content: the significant-phenomenon headline (if any), the narrative, and a freshness line. */
@Composable
private fun BulletinBody(bulletin: ForecastBulletin) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        bulletin.fenomenoSignificativo?.let { phenomenon ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE8A33D),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(text = phenomenon, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE8A33D))
            }
        }
        BulletinText.sentences(bulletin.texto).forEach { line ->
            Text(text = line, fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f))
        }
        FreshnessLine(bulletin.elaborado, bulletin.validezInicio)
    }
}

/** The medium-range outlook: its validity line, then one titled block per day. */
@Composable
private fun MedioBody(outlook: MedioPlazoForecast) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        outlook.validez?.let {
            Text(text = it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        outlook.days.forEach { day ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (day.diaNombre.isNotEmpty()) {
                    Text(
                        text = dayHeading(day),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                BulletinText.sentences(day.texto).forEach { line ->
                    Text(text = line, fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}

/** "Martes 1" — the weekday (title-cased) and day number for a medium-range block. */
private fun dayHeading(day: MedioPlazoForecast.Day): String {
    val name = day.diaNombre.lowercase(ES).replaceFirstChar { it.titlecase(ES) }
    return if (day.dia > 0) "$name ${day.dia}" else name
}

/** "Emitido 29 ago, 11:13 · válido Domingo 30 de agosto" — whichever parts AEMET stamped. */
@Composable
private fun FreshnessLine(elaborado: java.time.Instant?, validez: java.time.Instant?) {
    val parts = buildList {
        elaborado?.let { add(stringResource(R.string.sheet_national_issued, issuedText(it))) }
        validez?.let { add(stringResource(R.string.sheet_national_valid, validezText(it))) }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** A centred spinner while a deferred segment loads. */
@Composable
private fun SegmentSpinner() {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
    }
}

/** The quiet note shown when a segment's fetch came back empty (offline, rate-limited, or not published). */
@Composable
private fun SegmentUnavailable() {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.sheet_national_unavailable),
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

private val ES: Locale = Locale.forLanguageTag("es-ES")
