# Orientation: Android for someone who knows the iOS side

I built Aura on Apple platforms first, so I know Swift, SwiftUI, Xcode and Swift Package Manager reasonably well, and Android barely at all. This document maps what I already know onto what this repository uses, so I can find my way around without first learning all of Android. The deep, decision-by-decision version is in `aura-apps/specs/android-port.md`; this is the short, practical map.

## The tools, by analogy

- Android Studio is Xcode: the one official IDE. It manages the project, shows a Compose preview (like SwiftUI previews), runs the app, and has a log viewer called Logcat (roughly Console.app).
- Kotlin is Swift: the default language, similar in spirit (null-safety, data classes, lambdas, extension functions).
- Jetpack Compose is SwiftUI: a declarative UI toolkit. Composable functions re-run when state changes, exactly like SwiftUI views re-render.
- Gradle is Swift Package Manager, but heavier. It is the build system. The build files are `build.gradle.kts` (one per module) plus `settings.gradle.kts` at the root. Versions live in `gradle/libs.versions.toml`. Expect Gradle to be the least familiar and most fiddly part; the UI code will feel familiar much sooner than the build system will.
- The SDK is the platform frameworks. `adb` is the device bridge (install, logs, shell), the rough equivalent of the device tooling behind Xcode's Run.

## How this repository is shaped

Two Gradle modules:

- `:core` is `AuraKit`. Pure, portable logic, no UI, now behaviourally complete (the AEMET client, parsers, `WeatherSnapshot`, the solar/lunar math, the forecast prose). A good first file to read is `core/src/main/kotlin/com/mab/aura/core/solar/SolarTimes.kt`, a line-for-line port of `SolarTimes.swift`. Each type's test sits next to it under `src/test/`.
- `:app` is the app target. Compose UI. `app/src/main/kotlin/com/mab/aura/MainActivity.kt` is the entry point; it hosts the "Hoy" screen (`ui/hoy/`), the card stack (`ui/cards/`), the settings and locations screens, and the Glance widget (`widget/`), all fed by `:core`.

A module's source lives under `src/main/kotlin/...`, tests under `src/test/kotlin/...`, and Android resources (strings, themes) under `src/main/res/...`. The app manifest, `AndroidManifest.xml`, declares the launchable activity, a bit like the app's Info.plist plus the `@main` entry point combined.

## Kotlin next to Swift, the parts I keep reaching for

- `struct` becomes `data class`. `enum` becomes `enum class`. `protocol` becomes `interface`.
- Optionals: Swift `Int?` is Kotlin `Int?`. Swift `x!` is Kotlin `x!!`. Swift `if let` is Kotlin `?.let { }` or a smart-cast after a null check. Prefer `requireNotNull(x)` in tests to assert and unwrap in one step.
- `let` (constant) is `val`; `var` is `var`, same as Swift.
- String interpolation: Swift `\(x)` is Kotlin `$x` or `${x.foo}`.
- No `Foundation`. Dates are `java.time` (`Instant`, `ZoneId`, `DateTimeFormatter`). `java.time` exists from Android 8.0 (our minSdk 26), so no extra setup is needed.
- Locale is split on purpose since 1.1.1. Display formatters that render UI (weekday and month names) use `Locale.getDefault()`, so they follow the device or per-app language, English or Spanish. Keep `Locale.forLanguageTag("es-ES")` only where the code parses or capitalizes Spanish *source* data (the MITECO CSV, station names), and `en_US_POSIX` for machine date parsing. The generated forecast prose, the AEMET bulletins and the CAP alerts stay Spanish regardless of the UI language, because their source is Spanish.

## Compose next to SwiftUI

- A `View` struct becomes a function marked `@Composable`.
- `VStack` / `HStack` / `ZStack` become `Column` / `Row` / `Box`.
- `.padding()`, `.frame()` and other modifiers become a `Modifier` chain: `Modifier.fillMaxSize().padding(24.dp)`. This is the closest one-to-one in the whole port.
- `@State` becomes `remember { mutableStateOf(...) }`.
- `Text("...")`, `Spacer`, `Scaffold` all exist with familiar meanings. Material 3 (`androidx.compose.material3`) supplies the styled components and typography.
- Sizes use `.dp` (density-independent pixels), the Android counterpart of SwiftUI's points.

## Doing things

```bash
./gradlew :app:assembleDebug   # build the APK
./gradlew :core:test           # run unit tests
./gradlew installDebug         # install to a connected phone or emulator
adb logcat                     # watch device logs (Ctrl+C to stop)
```

In Android Studio, the same actions are the Run and Debug buttons and the Logcat panel. The visual Compose preview appears beside a `@Composable` marked with `@Preview`.

## Gotchas worth knowing early

- Gradle version alignment is strict. The pinned stack in `gradle/libs.versions.toml` interlocks; see the note in `CLAUDE.md` before changing anything.
- Android has no Lock Screen widget surface like iOS. The widget port moves to a Home Screen widget built with Jetpack Glance. That is a genuine feature move, not a one-to-one port.
- Background work is more restricted than on iOS (Doze, and aggressive battery savers on some manufacturers). The plan uses WorkManager and keeps the same idle-by-default discipline as the iOS app.
- SF Symbols do not exist on Android. The condition icons are [Meteocons](https://github.com/basmilius/weather-icons) (Bas Milius, MIT): bundled vector drawables, plus animated Lottie in the app's cards and the metric glyphs (sunrise, wind, humidity, UV). The Glance widget uses the static drawables, since RemoteViews can't animate.

When in doubt about why the port is shaped a certain way, the answer is almost always in `aura-apps/specs/android-port.md`.
