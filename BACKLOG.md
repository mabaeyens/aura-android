# Backlog

What is next on aura-android, roughly in order. This tracks the phased roadmap in the porting plan (`aura-apps/specs/android-port.md`, section 11).

## Done

- 2026-08-24: Installed the Android toolchain (keg-only JDK 21, Android Studio, SDK for API 36) and expanded the porting plan with a disk-footprint section and a file-by-file Swift-to-Kotlin map.
- 2026-08-24: Scaffolded the project: two Gradle modules (`:app` Compose Material 3, `:core`), building an 11 MB debug APK with passing unit tests. First logic port landed, `SolarTimes` from AuraKit, and `MainActivity` shows Madrid sun times as an end-to-end check.
- 2026-08-24: Set up the repo (README, license, orientation and release docs, changelog, privacy, gitignored `specs/` and `notes/`) and published it public at github.com/mabaeyens/aura-android.
- 2026-08-24: Ported the AEMET wire decodables to kotlinx.serialization (`MunicipioForecast`, `MunicipioHourly`, `UVIForecast`) in `:core/model/`, with decode tests pinning down the two ways kotlinx.serialization differs from Swift `Codable`: optionals need a `= null` default to tolerate a missing key, and the client `Json` needs `ignoreUnknownKeys = true`.
- 2026-08-24: Ported the first two leaf pure-logic types ahead of `WeatherSnapshot`, which depends on both: `WindDirection` (16-point rose, `core/wind/`) and `UVIndex` band logic (`core/uv/`). Parity tests, `WindDirectionTests.swift` ported verbatim. Left `UVIndex.glyph` out on purpose (SF Symbol names, deferred to the icon-set phase). Reason for the re-sequence: `WeatherSnapshot` is not a leaf, so its dependencies port first.

## Now

- Port `WeatherSnapshot` (the 675-line central view model every surface renders from). It is *not* a leaf: as a data class its fields still need `AirQuality`, `UVHourSlot` and `WeatherAlert` ported; its `make()` factory additionally needs `Location`, `StationObservation` and `ForecastBulletin` (fetch-side, so `make()` lands with the client/repository step). Port the struct + `HourSlot`/`DaySnapshot` + the pure computed helpers (`heroTemp`, `isNight`, `upcomingHours`, `nextSunEvent`, `hasCurrentHourData`, `activeAlert`) once the field types exist. `WindDirection` and `UVIndex`, two of its field types, are already done.
- Port `AEMETClient`'s two-call envelope-then-`datos` model with Retrofit and OkHttp. First add Retrofit/OkHttp to `gradle/libs.versions.toml` (not there yet); reuse the `Json { ignoreUnknownKeys = true }` config the model tests already assume.

## Next

- Port the rest of the pure logic: the `AirQuality` ICA scales, `MoonPhase` and `LunarTimes`, and `ForecastPhrase` (reproduce the seed derivation exactly, see the plan's parity note). (`WindDirection` and `UVIndex` are done.)
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
