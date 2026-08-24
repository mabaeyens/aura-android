# Backlog

What is next on aura-android, roughly in order. This tracks the phased roadmap in the porting plan (`aura-apps/specs/android-port.md`, section 11).

## Done

- 2026-08-24: Installed the Android toolchain (keg-only JDK 21, Android Studio, SDK for API 36) and expanded the porting plan with a disk-footprint section and a file-by-file Swift-to-Kotlin map.
- 2026-08-24: Scaffolded the project: two Gradle modules (`:app` Compose Material 3, `:core`), building an 11 MB debug APK with passing unit tests. First logic port landed, `SolarTimes` from AuraKit, and `MainActivity` shows Madrid sun times as an end-to-end check.
- 2026-08-24: Set up the repo (README, license, orientation and release docs, changelog, privacy, gitignored `specs/` and `notes/`) and published it public at github.com/mabaeyens/aura-android.

## Now

- Port the core models to kotlinx.serialization: `WeatherSnapshot` and the AEMET decodables (`MunicipioForecast`, `MunicipioHourly`, `UVIForecast`).
- Port `AEMETClient`'s two-call envelope-then-`datos` model with Retrofit and OkHttp.

## Next

- Port the rest of the pure logic: `WindDirection`, `UVIndex`, the `AirQuality` ICA scales, `MoonPhase` and `LunarTimes`, and `ForecastPhrase` (reproduce the seed derivation exactly, see the plan's parity note).
- Build the location layer per the local `specs/location.md`: a `:app` `LocationProvider` over the AOSP `LocationManager` that feeds a `Coordinate` into `SolarTimes` and the forecast. Handle the three no-fix states (permission denied, services off, no fix).
- Stand up the repository layer (the `AEMETService` equivalent) and a first real "Hoy" screen over ported cards and a Compose `AuraSky`.

## Later

- Home Screen widget with Jetpack Glance, a DataStore shared read path between app and widget, and WorkManager for the refresh cadence.
- Asset pipeline: WebP-convert and downscale the hero art (the 442 MB problem, plan appendix A). Phone-only drops the watch and iPad variants automatically.
- Signed release APK, then Play internal testing (see `docs/RELEASE.md`).

## Standing notes

- The AEMET key is shared and never committed (plan section 9).
- Location uses the platform AOSP `LocationManager` (via `LocationManagerCompat.getCurrentLocation`), never Google Play Services. This keeps Aura working on GMS-less devices and behaving the same on every OEM as on the emulator. `:app` acquires location and hands `:core` a plain `Coordinate`; `:core` stays free of any `android.*` or `com.google.*` import. Full spec in the local `specs/location.md`.
- Test-device plan: a Pixel is the clean development reference; the real OEM variety (Samsung, Xiaomi) comes from Samsung Remote Test Lab and Firebase Test Lab rather than more hardware. Watch for OEM background-killing (WorkManager for any refresh, never a bare service).
- The version stack is pinned deliberately. Read `CLAUDE.md` before bumping anything.
- MITECO's air-quality host has an incomplete TLS chain. The fix is a scoped Network Security Config trust-anchor for that domain only, not a global bypass (plan sections 5 and 10).
