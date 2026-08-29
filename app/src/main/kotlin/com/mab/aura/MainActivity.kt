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
import com.mab.aura.ui.about.AcercaScreen
import com.mab.aura.ui.help.AyudaScreen
import com.mab.aura.ui.help.FreshnessScreen
import com.mab.aura.ui.hoy.HoyScreen
import com.mab.aura.ui.locations.AddLocationScreen
import com.mab.aura.ui.locations.LocationsScreen
import com.mab.aura.ui.settings.SettingsScreen
import com.mab.aura.ui.theme.AuraTheme
import com.mab.aura.work.WeatherRefreshScheduler

/** The app's destinations. Hoy is the root; every other screen returns to a statically-known parent (see below). */
private enum class Screen { Hoy, Settings, Locations, AddLocation, Help, DataFreshness, About }

/**
 * The app's single activity. It hosts the live "Hoy" screen (a full-bleed sky, its own surface), "Ajustes",
 * and the "Ubicaciones" pair, swapping between them with a small saved screen state rather than a navigation
 * library.
 *
 * A note for anyone coming from iOS: SwiftUI reaches for `NavigationStack` almost by reflex. Here every screen
 * has a *single, fixed* parent — Ajustes and Ubicaciones return to Hoy, and Añadir returns to Ubicaciones — so
 * a `when` over a `rememberSaveable` enum, with each screen's Back routing to its known parent, stays the clear,
 * dependency-free equivalent without needing a real back stack. If a screen ever gains more than one possible
 * caller, that's the point to adopt Jetpack Navigation-Compose and let it own the stack.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure the background widget refresh is scheduled. Enqueued as unique/KEEP, so this is a no-op
        // after the first launch and never stacks duplicate work (see WeatherRefreshScheduler).
        WeatherRefreshScheduler.schedule(this)
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Hoy) }
                when (screen) {
                    Screen.Hoy -> HoyScreen(
                        modifier = Modifier.fillMaxSize(),
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenLocations = { screen = Screen.Locations },
                        onOpenHelp = { screen = Screen.Help },
                        onOpenAbout = { screen = Screen.About },
                    )

                    Screen.Settings -> {
                        // Route the system Back gesture/button to the same "return to Hoy" action as the top bar.
                        BackHandler { screen = Screen.Hoy }
                        SettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { screen = Screen.Hoy },
                        )
                    }

                    Screen.Locations -> {
                        BackHandler { screen = Screen.Hoy }
                        LocationsScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { screen = Screen.Hoy },
                            onAddLocation = { screen = Screen.AddLocation },
                        )
                    }

                    Screen.AddLocation -> {
                        BackHandler { screen = Screen.Locations }
                        AddLocationScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { screen = Screen.Locations },
                        )
                    }

                    Screen.Help -> {
                        BackHandler { screen = Screen.Hoy }
                        AyudaScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { screen = Screen.Hoy },
                            onOpenFreshness = { screen = Screen.DataFreshness },
                        )
                    }

                    // The one screen whose parent is Ayuda, not Hoy: opened from a link inside Help, so Back
                    // returns there rather than all the way to Hoy.
                    Screen.DataFreshness -> {
                        BackHandler { screen = Screen.Help }
                        FreshnessScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { screen = Screen.Help },
                        )
                    }

                    Screen.About -> {
                        BackHandler { screen = Screen.Hoy }
                        AcercaScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { screen = Screen.Hoy },
                        )
                    }
                }
            }
        }
    }
}
