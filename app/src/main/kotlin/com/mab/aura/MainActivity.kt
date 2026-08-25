package com.mab.aura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mab.aura.ui.hoy.HoyScreen
import com.mab.aura.ui.theme.AuraTheme

/**
 * The app's single activity. It hosts the live "Hoy" screen, which draws its own full-bleed sky background
 * edge to edge (so no Scaffold and no surface behind it — the sky is the surface). Everything below is
 * Compose; the ViewModel and repository handle the data.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                HoyScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
