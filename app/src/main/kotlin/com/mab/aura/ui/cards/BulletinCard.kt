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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.aura.core.text.BulletinText
import com.mab.aura.ui.theme.Palette

/**
 * The AEMET narrative "Predicción" bulletin, one line per sentence over [BulletinText.sentences].
 * When AEMET flags a phenomenon (a named risk like "Tormentas"), it heads the card in warning orange.
 * Ported from `AuraBulletinCard` in `AuraAppCards.swift`.
 */
@Composable
fun AuraBulletinCard(
    phenomenon: String?,
    text: String,
    size: AuraSize,
    modifier: Modifier = Modifier,
) {
    AuraSection("PREDICCIÓN", size) {
        AuraCard(size, modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (phenomenon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Palette.tempOrange,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = phenomenon,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.tempOrange,
                        )
                    }
                }
                BulletinText.sentences(text).forEach { line ->
                    Text(
                        text = line,
                        fontSize = 19.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
