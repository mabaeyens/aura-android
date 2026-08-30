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

## R8 shrinking and obfuscation (on)

The release build runs R8 (`release { isMinifyEnabled = true; isShrinkResources = true }`), so the store build is shrunk, optimized and obfuscated. This roughly halved the artifact: the release APK went from 21.9 MB to 11.9 MB, the AAB from 21.6 MB to 15.3 MB. Debug builds stay unminified, so stack traces and the Compose tooling keep working. R8 runs on the pinned AGP 8.13.2; it never needed the AGP 9 upgrade the Console's optimization nudge conflates it with.

Two things the keep rules in `app/proguard-rules.pro` protect, because R8 breakage only shows at runtime, not at build time:

- **kotlinx.serialization.** The `@Serializable` types in `:core` back the on-disk JSON caches the app and the widget read. The rules keep each type's Companion and its generated `serializer()`, so obfuscation can't silently corrupt a saved snapshot. The library ships the same rules as consumer rules; they are pinned explicitly here because the persistent cache is the highest-risk surface.
- **Missing-class `-dontwarn` for build-time-only annotations.** Apache Commons Compress references optional compression codecs Aura never uses (xz, zstd, brotli), and Tink (behind `androidx.security-crypto`, which encrypts the AEMET key) references Error Prone annotations that never ship at runtime. Both would otherwise fail R8's missing-class check. The Tink list is exactly what AGP generated in `build/.../missing_rules.txt`.

Everything else Aura uses (Glance, WorkManager, DataStore, OkHttp) ships its own consumer rules, and the manifest entry points are kept by AGP automatically, so the rules file stays small. The `res/raw` assets (Lottie animations, the FNMT cert) are referenced by explicit `R.raw.*`/`@raw` ids and the hero art lives in `assets/` (which the resource shrinker never touches), so none of it needs a `keep.xml`.

The R8 mapping is produced at `app/build/outputs/mapping/release/mapping.txt` on every release build, and AGP also embeds it inside the AAB at `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`. So there is no separate mapping upload on Play: it extracts the embedded copy automatically when it ingests the bundle, and crash and ANR stack traces de-obfuscate on their own. The `mapping.txt` on disk is just the local copy to keep with the release.

Play may also warn that the bundle "contains native code" with no debug symbols. That is a recommendation, not a blocker, and it is safe to dismiss: the only native code is two tiny already-stripped AndroidX prebuilts (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`), Aura's own code is pure Kotlin covered by the mapping above, and satisfying the warning would need `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` plus installing the NDK, a toolchain addition the project avoids. Revisit only if a native crash inside one of those libs ever shows up.

Verified on the emulator from a signed release install: the app launches under obfuscation, the AEMET key saves and loads through the encrypted store, a live fetch deserializes and renders (hero summary, multi-day forecast, sun-path card, forecast bulletin, news feed), the hero art loads, and the English-chrome / Spanish-data localization boundary holds. A physical-device pass belongs to the next real release.

## The release skill

Releases run through `/aura-android-release`, the Android sibling of the iOS `/aura-release`. It lives user-global at `~/.claude/skills/aura-android-release/`, not in this repo. It runs every deterministic pass and stops at "ready to upload" by design, because the Play Developer API can't do the app-review steps and the last mile stays manual Console web work (create the release, upload the AAB, edit the listing, roll out). Each release it: decides the next version (`versionCode` always +1, `versionName` minor for a feature batch or patch for fixes), bumps `app/build.gradle.kts`, writes the `CHANGELOG.md` entry, fills the bilingual `notes/play-listing.md` copy (the "What's new" block in es-ES and en-US under 500 characters each, plus the full-description feature bullets under 4000), builds and verifies the signed AAB (`:core` tests then `bundleRelease`, confirming the mapping is embedded), commits and tags `v<version>`, and hands over a copy/paste-ready block with the AAB path and the Console steps.

What is still not automated is the upload itself. That waits on a repeatable hands-off path (a service-account JSON plus Gradle Play Publisher); until it exists, the skill preparing everything up to the upload is the right shape.
