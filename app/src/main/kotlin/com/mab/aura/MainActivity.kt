package com.mab.aura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.aura.core.solar.SolarTimes
import com.mab.aura.ui.theme.AuraTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    HomeScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    // Madrid, as a first end-to-end proof that the ported :core module computes
    // real values and the app renders them. This is scaffolding, not the app.
    val madridLat = 40.4168
    val madridLon = -3.7038
    val zone = ZoneId.of("Europe/Madrid")
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-ES")) }
    val solar = remember { SolarTimes.compute(Instant.now(), madridLat, madridLon) }
    val sunrise = solar.sunrise?.atZone(zone)?.format(fmt) ?: "n/d"
    val sunset = solar.sunset?.atZone(zone)?.format(fmt) ?: "n/d"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Aura", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text("Android, esqueleto Compose", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Text("Madrid, hoy", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Amanecer $sunrise, ocaso $sunset", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Text(
            "SolarTimes portado desde AuraKit (primer trozo del modulo :core)",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
