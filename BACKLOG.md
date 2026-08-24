# Backlog

What is next on aura-android, roughly in order. This tracks the phased roadmap in the porting plan (`aura-apps/specs/android-port.md`, section 11).

## Now

- Port the core models to kotlinx.serialization: `WeatherSnapshot` and the AEMET decodables (`MunicipioForecast`, `MunicipioHourly`, `UVIForecast`).
- Port `AEMETClient`'s two-call envelope-then-`datos` model with Retrofit and OkHttp.

## Next

- Port the rest of the pure logic: `WindDirection`, `UVIndex`, the `AirQuality` ICA scales, `MoonPhase` and `LunarTimes`, and `ForecastPhrase` (reproduce the seed derivation exactly, see the plan's parity note).
- Stand up the repository layer (the `AEMETService` equivalent) and a first real "Hoy" screen over ported cards and a Compose `AuraSky`.

## Later

- Home Screen widget with Jetpack Glance, a DataStore shared read path between app and widget, and WorkManager for the refresh cadence.
- Asset pipeline: WebP-convert and downscale the hero art (the 442 MB problem, plan appendix A). Phone-only drops the watch and iPad variants automatically.
- Signed release APK, then Play internal testing (see `docs/RELEASE.md`).

## Standing notes

- The AEMET key is shared and never committed (plan section 9).
- The version stack is pinned deliberately. Read `CLAUDE.md` before bumping anything.
- MITECO's air-quality host has an incomplete TLS chain. The fix is a scoped Network Security Config trust-anchor for that domain only, not a global bypass (plan sections 5 and 10).
