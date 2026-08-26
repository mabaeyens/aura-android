# Releasing aura-android

There is no release pipeline yet. This document is the plan for when there is one, so the steps are written down before I need them. The reasoning behind each distribution choice is in `aura-apps/specs/android-port.md`, section 8.

## Today

Two builds:

```bash
./gradlew :app:assembleDebug     # unsigned debug, for my own device and the emulator
./gradlew :app:assembleRelease   # signed release APK (needs keystore.properties, see Phase 1)
```

## Phase 1: a signed APK for a first tester or two — done

Release signing is wired, so `assembleRelease` produces a directly-installable signed APK with no store involved.

1. The upload keystore lives outside the repo at `~/.aura-android/aura-upload.jks`, so a `git clean` in the project can never delete it, and it is backed up off the machine. It is a 4096-bit RSA key valid well past 2050. If it is ever lost, an existing install can only be replaced by a fresh reinstall.
2. `app/build.gradle.kts` reads the keystore path and passwords from a gitignored `keystore.properties` at the repo root, never from committed code. When that file is absent (a clean checkout or CI) the release build still runs, just unsigned.
3. Build the signed APK with `./gradlew :app:assembleRelease`. It lands at `app/build/outputs/apk/release/app-release.apk`, signed with APK Signature Scheme v2, which is enough for minSdk 26.
4. Install it directly over USB or wireless adb (`adb install -r <apk>`), or send it as a link. The installer taps through one "allow installing from this source" prompt, a per-app toggle on modern Android, not a scary global setting.

## Phase 2: Play internal testing

Once a wider circle of family and friends wants it, move to Play internal testing (the $25 developer account, a minimal Play Console listing, but none of the 12-tester production gate). Internal testing gives them the normal "open the Play Store, tap install" flow. Play requires an Android App Bundle (`.aab`), built with `./gradlew :app:bundleRelease`, and uses Play App Signing on top of the upload key.

## Why there is no release skill yet

The Aura iOS app has a release skill because its pipeline is real and repeatable. Here the keystore, the signing config and the distribution channel do not exist yet, so there is nothing stable to automate. A skill comes after phase 1 is set up by hand once, not before.
