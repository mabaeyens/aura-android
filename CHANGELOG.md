# Changelog

Notable changes to aura-android, newest first.

## [1.2.0] - 2026-08-30

More of the iOS cards ported across, plus a couple of clarity and rate-limit fixes.

- New AEMET national text forecast card: the official plain-language outlook for Spain, alongside the existing regional bulletin.
- New surface analysis map card, the last iOS card that was still missing, so the Android layout now matches the iOS one card for card.
- The hero temperature now always leads with the current-hour forecast and never swaps in the observed reading, so it matches the hourly strip and the widget exactly.
- New data-freshness help page that explains when each reading updates, so it is clear why the observation, the forecast and the warnings refresh at different times.
- Softer wording on the "too many requests" (429) notice, and pull-to-refresh is now capped at one AEMET call a minute so a busy tap can't trip the rate limit.

## [1.1.1] - 2026-08-27

A corrected build resubmitted after review feedback, plus an English UI and a widget framing fix.

- Every surface now names its open-data sources with links (AEMET OpenData, MITECO ICA, Copernicus CAMS via Open-Meteo) and carries a clear disclaimer that Aura is an independent app, not affiliated with or representing AEMET, MITECO, or any government entity.
- English UI: the whole app chrome, the settings, help and about screens, the cards, the sheets and the widget now follow the device language, with English alongside Spanish. The generated forecast text and the official Spanish source content stay in Spanish.
- Home-screen widget framing: on a wide tile the art now trims the top sky so the scene reads instead of empty sky, while a tall tile keeps its full framing.
- Weekday and month names in the app now follow the device language.

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
