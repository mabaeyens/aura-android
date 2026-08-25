package com.mab.aura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `@Preview` for [ConditionGlyph]: every distinct condition, drawn once by day and once by night, so the
 * whole mapping (and the blue-moon / white-snow tint rules) is visible at a glance in Android Studio.
 */

// One representative AEMET code per glyph branch, with a short label.
private val PREVIEW_CODES = listOf(
    "11" to "clear",
    "12" to "few",
    "14" to "cloud",
    "16" to "haze",
    "23" to "l.rain",
    "24" to "rain",
    "25" to "h.rain",
    "33" to "snow",
    "71" to "l.snow",
    "51" to "storm",
    "53" to "storm+r",
)

@Preview(name = "ConditionGlyph · day + night", widthDp = 360, backgroundColor = 0xFF202433)
@Composable
private fun ConditionGlyphPreview() {
    // White ambient content colour so the auto tint rules show against the dark panel: most glyphs read
    // white, the clear-night moon reads blue, the snowflake is forced white — none is passed an explicit tint.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier = Modifier.background(Color(0xFF202433)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (night in listOf(false, true)) {
                Text(if (night) "Night" else "Day", color = Color.White, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((code, label) in PREVIEW_CODES) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ConditionGlyph(sky = code, isNight = night, slot = 18.dp)
                            Text(label, color = Color.White, fontSize = 8.sp)
                        }
                    }
                }
            }
        }
    }
}
