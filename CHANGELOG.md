# Changelog

Notable changes to aura-android, newest first.

## [1.1.0] - 2026-08-26

First public build, for Play internal testing. I jumped straight to 1.1.0 so the Android version lines up with the parallel iOS 1.1.0; there was no public 0.1.0 or 1.0.0 on Android, and there is no tip jar yet.

- Full Hoy screen ported from iOS: an on-device natural-language headline, current conditions, the hourly strip, a seven-day forecast, the sunrise to sunset arc, wind on the Beaufort scale, air quality (MITECO ICA), the UV index (CAMS via Open-Meteo), the nearest radar, the official AEMET bulletin, and public-source weather news, all over a live sun-tracking sky.
- Home-screen widget (Jetpack Glance) showing current conditions and the hourly strip.
- Official AEMET warnings (CAP) matched to the municipality by province, and the real observed temperature from the nearest AEMET station.
- Private by design: the AEMET key is stored encrypted on the device, there is no Aura server, and only the municipality, never the device's position, leaves the phone to fetch the public forecast.
- Larger condition glyphs on the cards and the widget, to match the iOS sizing.
- The hourly observation feed is gated on its own fint clock, so it only refetches when a new reading is actually due.

## [0.1.0] - 2026-08-24

First scaffold. Not a release, just the project skeleton building end to end.

- Two-module Gradle project: `:app` (Jetpack Compose, Material 3) and `:core` (portable logic ported from the Swift `AuraKit`).
- First logic port: `SolarTimes`, the NOAA sunrise/sunset math, with a passing unit test.
- `MainActivity` renders Madrid's sunrise and sunset for today, computed by `:core`, as an end-to-end wiring check.
- Toolchain pinned: AGP 8.13.2, Kotlin 2.4.10, Compose BOM 2025.10.01, Gradle 8.14.5, minSdk 26, targetSdk 36.
- Repository set up with README, license, orientation and release docs, and the standard local-only `specs/` and `notes/` convention.
