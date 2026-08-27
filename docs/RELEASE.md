# Releasing aura-android

Signing is wired and the app is in Play internal testing, so the path is real now, not just planned. This document is the working record of it. The reasoning behind each distribution choice is in `aura-apps/specs/android-port.md`, section 8.

The current version is 1.1.1 (`versionCode` 2), the corrected resubmission after the 1.1.0 review feedback (see `CHANGELOG.md`). `versionCode` must strictly increase and a used code can never be reused, so the next release is code 3.

## Today

Three builds:

```bash
./gradlew :app:assembleDebug     # unsigned debug, for my own device and the emulator
./gradlew :app:assembleRelease   # signed release APK (needs keystore.properties, see Phase 1)
./gradlew :app:bundleRelease     # signed Android App Bundle (.aab) for Play, the upload artifact
```

The bundle lands at `app/build/outputs/bundle/release/app-release.aab`. That is what Play internal testing takes; the APK is only for a direct sideload to my own device.

## Phase 1: a signed APK for a first tester or two (done)

Release signing is wired, so `assembleRelease` produces a directly-installable signed APK with no store involved.

1. The upload keystore lives outside the repo at `~/.aura-android/aura-upload.jks`, so a `git clean` in the project can never delete it, and it is backed up off the machine. It is a 4096-bit RSA key valid well past 2050. If it is ever lost, an existing install can only be replaced by a fresh reinstall.
2. `app/build.gradle.kts` reads the keystore path and passwords from a gitignored `keystore.properties` at the repo root, never from committed code. When that file is absent (a clean checkout or CI) the release build still runs, just unsigned.
3. Build the signed APK with `./gradlew :app:assembleRelease`. It lands at `app/build/outputs/apk/release/app-release.apk`, signed with APK Signature Scheme v2, which is enough for minSdk 26.
4. Install it directly over USB or wireless adb (`adb install -r <apk>`), or send it as a link. The installer taps through one "allow installing from this source" prompt, a per-app toggle on modern Android, not a scary global setting.

## Phase 2: Play internal testing (in progress)

The app is registered on Play as `com.mab.Aura` (capital A, the install/store identity, permanently reserved once uploaded, so a rejection is fixed by a new version into the *same* app, never a new one; the `namespace` stays lowercase `com.mab.aura`). Internal testing gives testers the normal "open the Play Store, tap install" flow, and sits outside the closed-testing tester-count/duration gate. Play takes the `.aab` from `bundleRelease` and adds Play App Signing on top of the `AURA-UPL` upload key.

Status: 1.1.0 was submitted and rejected on the government-information policy (the copy read as an official app). 1.1.1 is the corrected resubmission: every surface now names its open-data sources and carries the independent, non-affiliated disclaimer, plus the English UI and the widget framing fix. The listing copy, the data-safety answers and every App-content declaration are prepared in the gitignored `notes/play-listing.md`; the store image assets (icon, feature graphic, screenshots) are in `docs/store/`. The remaining work is the interactive Play Console clicks, which need my hands: upload the AAB to the internal track, update the store listing text, and correct the App content declarations so the app reads as independent.

## Not yet optimized (R8)

The release build ships unshrunk (`isMinifyEnabled = false`), so Play's app-optimization score is Low. Enabling R8 shrinking + obfuscation is deferred deliberately (it needs keep rules and a full on-device verification pass, and it does not need the AGP 9 upgrade the Console suggests). Tracked in the local `specs/r8-optimization.md`; the durable note lands here once it ships.

## Why there is no release skill yet

The Aura iOS app has a release skill because its pipeline is real and repeatable. Here the last mile is still manual Play Console web work (create/upload, listing edits, content declarations), and the Play Developer API can't do the app-creation and review steps. A skill comes once the upload path is a repeatable hands-off step (a service-account JSON + Gradle Play Publisher), not before.
