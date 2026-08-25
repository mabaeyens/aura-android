package com.mab.aura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mab.aura.ui.hoy.HoyScreen
import com.mab.aura.ui.settings.SettingsScreen
import com.mab.aura.ui.theme.AuraTheme

/** The app's destinations. Two for now; a real back stack (Navigation-Compose) lands with favourites. */
private enum class Screen { Hoy, Settings }

/**
 * The app's single activity. It hosts the live "Hoy" screen (a full-bleed sky, its own surface) and the
 * "Ajustes" screen, swapping between them with a small saved screen state rather than a navigation library.
 *
 * A note for anyone coming from iOS: SwiftUI reaches for `NavigationStack` almost by reflex, but with only
 * two destinations a `when` over a `rememberSaveable` enum is the clearer, dependency-free equivalent — it is
 * literally "show this view or that one". Once there are three or more screens (favourites, add-location) this
 * moves to Jetpack Navigation-Compose, which owns a proper back stack.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Hoy) }
                when (screen) {
                    Screen.Hoy -> HoyScreen(
                        modifier = Modifier.fillMaxSize(),
                        onOpenSettings = { screen = Screen.Settings },
                    )

                    Screen.Settings -> {
                        // Route the system Back gesture/button to the same "return to Hoy" action as the top bar.
                        BackHandler { screen = Screen.Hoy }
                        SettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { screen = Screen.Hoy },
                        )
                    }
                }
            }
        }
    }
}
