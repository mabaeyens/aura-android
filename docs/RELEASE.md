# Releasing aura-android

There is no release pipeline yet. This document is the plan for when there is one, so the steps are written down before I need them. The reasoning behind each distribution choice is in `aura-apps/specs/android-port.md`, section 8.

## Today

Debug builds only:

```bash
./gradlew :app:assembleDebug
```

The debug APK is not signed for distribution and is only for my own device and the emulator.

## Phase 1: a signed APK for a first tester or two

The first real distribution is a directly-installed signed APK, no store involved:

1. Create an upload keystore once, and keep it out of the repo. It is gitignored (`*.jks`, `*.keystore`, `keystore.properties`), and if it is ever lost, an existing install can only be replaced by a fresh reinstall, so it is backed up off the machine.
2. Add a signing config that reads the keystore path and passwords from a local, gitignored `keystore.properties`, never from committed code.
3. Build the release APK with `./gradlew :app:assembleRelease`.
4. Send the signed APK as a link. The installer taps through one "allow installing from this source" prompt, which is a per-app toggle on modern Android, not a scary global setting.

## Phase 2: Play internal testing

Once a wider circle of family and friends wants it, move to Play internal testing (the $25 developer account, a minimal Play Console listing, but none of the 12-tester production gate). Internal testing gives them the normal "open the Play Store, tap install" flow. Play requires an Android App Bundle (`.aab`), built with `./gradlew :app:bundleRelease`, and uses Play App Signing on top of the upload key.

## Why there is no release skill yet

The Aura iOS app has a release skill because its pipeline is real and repeatable. Here the keystore, the signing config and the distribution channel do not exist yet, so there is nothing stable to automate. A skill comes after phase 1 is set up by hand once, not before.
