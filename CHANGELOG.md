# Changelog

Notable changes to aura-android, newest first.

## [0.1.0] - 2026-08-24

First scaffold. Not a release, just the project skeleton building end to end.

- Two-module Gradle project: `:app` (Jetpack Compose, Material 3) and `:core` (portable logic ported from the Swift `AuraKit`).
- First logic port: `SolarTimes`, the NOAA sunrise/sunset math, with a passing unit test.
- `MainActivity` renders Madrid's sunrise and sunset for today, computed by `:core`, as an end-to-end wiring check.
- Toolchain pinned: AGP 8.13.2, Kotlin 2.4.10, Compose BOM 2025.10.01, Gradle 8.14.5, minSdk 26, targetSdk 36.
- Repository set up with README, license, orientation and release docs, and the standard local-only `specs/` and `notes/` convention.
