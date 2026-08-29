package com.mab.aura.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.aura.R
import com.mab.aura.core.model.ForecastBulletin
import com.mab.aura.core.model.MedioPlazoForecast
import com.mab.aura.core.text.BulletinText
import com.mab.aura.ui.sheets.AuraDetailCard
import com.mab.aura.ui.sheets.AuraNationalForecastSheet
import com.mab.aura.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the "Predicción nacional" card and its sheet need. [today] is the resolved national narrative for
 * today (already fetched on the refresh path, ≤1/6 h); the three `load…` functions fetch the sheet's other
 * segments **lazily**, only when that segment is opened — kept as suspend lambdas over the repository so a
 * closed sheet costs nothing. Assembled by `HoyViewModel` and passed down like [AuraSurfaceInfo].
 */
data class NationalForecastState(
    val today: ForecastBulletin,
    val loadManana: suspend () -> ForecastBulletin?,
    val loadPasadoManana: suspend () -> ForecastBulletin?,
    val loadMedioPlazo: suspend () -> MedioPlazoForecast?,
)

/**
 * AEMET's national narrative forecast — the España-level twin of the community "Predicción" bulletin. The card
 * body shows today's significant-phenomena line (when AEMET flags one) and the synopsis lead; tapping it opens
 * a sheet with the full day-by-day outlook (Hoy / Mañana / Pasado mañana / Medio plazo).
 *
 * A small validity line names the day the words actually cover: on a quiet day AEMET's national `hoy` product
 * can be stale, so the resolver falls back to showing `manana` as today — this line is how the reader sees that
 * (see [NationalForecastState]). The narrative itself is AEMET's Spanish text, shown verbatim; only the card
 * chrome is localized, matching the surface and community-bulletin cards.
 */
@Composable
fun AuraNationalForecastCard(
    state: NationalForecastState,
    size: AuraSize,
    modifier: Modifier = Modifier,
) {
    val today = state.today
    AuraSection(stringResource(R.string.card_national_title), size, modifier = modifier) {
        AuraDetailCard(size, sheet = { onClose -> AuraNationalForecastSheet(state = state, onClose = onClose) }) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (today.fenomenoSignificativo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Palette.tempOrange,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = today.fenomenoSignificativo!!,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.tempOrange,
                        )
                    }
                }

                // The synopsis lead only (the first couple of sentences); the full text lives in the sheet.
                Text(
                    text = lead(today.texto),
                    fontSize = 19.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth(),
                )

                today.validezInicio?.let { valid ->
                    Text(
                        text = stringResource(R.string.card_national_valid, validezText(valid)),
                        fontSize = size.smallSize - 1,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Text(
                    text = stringResource(R.string.card_national_hint),
                    fontSize = size.smallSize - 1,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The first two sentences of the narrative: enough to lead the card, with the rest saved for the sheet. */
private fun lead(text: String): String =
    BulletinText.sentences(text).take(2).joinToString(" ")

// --- Date formatting for the freshness lines ------------------------------------------------------------
// AEMET stamps these in Spanish peninsular civil time, so the dates read in Europe/Madrid and in Spanish (they
// describe AEMET's data, like the bulletin body; only the surrounding label is localized).

private val ES: Locale = Locale.forLanguageTag("es-ES")
private val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
private val validezFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES).withZone(MADRID)
private val issuedFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", ES).withZone(MADRID)

/** "Domingo 30 de agosto" — the day the forecast is valid for. */
internal fun validezText(instant: Instant): String =
    validezFmt.format(instant).replaceFirstChar { if (it.isLowerCase()) it.titlecase(ES) else it.toString() }

/** "29 ago, 11:13" — when AEMET issued the bulletin. */
internal fun issuedText(instant: Instant): String = issuedFmt.format(instant)
