# aura-android

Aura is my personal weather app for Spain, powered by AEMET OpenData. This repository is the Android port: phone-only, native Kotlin and Jetpack Compose. The iOS/watchOS original lives in a separate repository, `aura-apps`, and the full porting plan (why every choice was made, since I am new to Android) is written up there in `specs/android-port.md`.

The port is feature-complete on the phone. The current version is 1.1.1, prepared for Google Play internal testing (see `CHANGELOG.md`).

## What the app does

- A full "Hoy" screen: an on-device natural-language headline, current conditions, the hourly strip, a seven-day forecast, the sunrise to sunset arc, wind on the Beaufort scale, air quality (MITECO ICA), the UV index, the nearest radar, the official AEMET bulletin, and public-source weather news, all over a live sun-tracking sky with illustrated hero art.
- A Home Screen widget (Jetpack Glance) that shows the active location's conditions and hourly strip from the shared cache, refreshed in the background by WorkManager.
- Saved locations you manage by search or device fix, official AEMET warnings matched to your municipality, and the real observed temperature from the nearest AEMET station.
- English and Spanish UI that follows the device language. Private by design: the AEMET key is stored encrypted on the device, there is no Aura server, and only the municipality ever leaves the phone.

## Project layout

- `:app` is the phone app: Jetpack Compose with Material 3, the "Hoy" screen and its card stack, the settings and locations screens, and the Glance widget. Entry point is `app/src/main/kotlin/com/mab/aura/MainActivity.kt`. This is Android's rough equivalent of the SwiftUI app target.
- `:core` is the portable logic, hand-ported from the Swift `AuraKit` package one file at a time, and now behaviourally complete: the AEMET client, the parsers, `WeatherSnapshot` and its factory, the solar/lunar math, and the on-device forecast prose. This is the rough equivalent of `AuraKit`. It holds no `android.*` import.

If you are coming from the iOS side (Swift, SwiftUI, Xcode, Swift Package Manager) and Android is unfamiliar, read `docs/ORIENTATION.md` first. It maps what you already know onto what this repository uses.

## Toolchain

- JDK 21, Android Studio, and the Android SDK (installed under `~/Library/Android/sdk` on my machine).
- AGP 8.13.2, Kotlin 2.4.10, Compose BOM 2025.10.01, Gradle 8.14.5.
- `minSdk` 26 (Android 8.0), `targetSdk` and `compileSdk` 36 (Android 16).

The versions are pinned deliberately and interlock. Before bumping any of them, read the note in `CLAUDE.md`: the newest 2026 libraries pull in Android Gradle Plugin 9 and compileSdk 37, which change how Kotlin and Compose are wired, so I stayed one step back on the well-documented path on purpose.

## Build, test, run

From the project root:

```bash
./gradlew :app:assembleDebug   # build the debug APK (app/build/outputs/apk/debug/)
./gradlew :core:test           # run the :core unit tests
./gradlew installDebug         # install onto a connected device or running emulator
```

If Gradle cannot find Java or the SDK, make sure `JAVA_HOME` points at the JDK and `ANDROID_HOME` at the SDK. On my machine both are set in `~/.zshrc`. Opening the folder in Android Studio wires all of this automatically and gives you the visual Compose preview and the Run button.

## Status and scope

Phone only. No Wear OS and no tablet layout, by design. The full "Hoy" card stack and the Home Screen widget are ported and shipping; what remains is the Play Console rollout and optional polish. See `BACKLOG.md` for what is done and what is next, and `docs/RELEASE.md` for the release path.

## Attribution

Aura reads from open-data repositories with public access: weather from [AEMET OpenData](https://opendata.aemet.es), air quality from [MITECO](https://www.miteco.gob.es) (ICA), and the UV curve from [Copernicus (CAMS)](https://atmosphere.copernicus.eu) via Open-Meteo where shown. Weather icons are [Meteocons](https://github.com/basmilius/weather-icons) by Bas Milius, used under the MIT licence.

Aura is an independent app. It is not affiliated with, and does not represent, AEMET, MITECO, or any government entity.

Built with the help of Claude Code.
