# Project-specific R8 rules for the shrunk, obfuscated release build (app/build.gradle.kts,
# release { isMinifyEnabled = true }). Anything R8 can only reach by reflection or by name has to be
# named here, because R8 breakage shows up at runtime, not at build time.
#
# Deliberately minimal. Most of what Aura uses ships its own consumer rules inside its AAR/JAR, which
# R8 applies automatically, so this file does NOT need to repeat them:
#   - AndroidX Glance (the widget), WorkManager (the background refresh), DataStore and
#     security-crypto bundle their own keeps.
#   - OkHttp bundles the -dontwarn rules for its optional TLS providers (conscrypt, bouncycastle).
#   - The manifest entry points (MainActivity, AuraWidgetReceiver, WidgetConfigActivity) are kept by
#     AGP automatically because they are declared in AndroidManifest.xml.
# Only the two things that are NOT covered out of the box are pinned below.

# ---------------------------------------------------------------------------
# 1. kotlinx.serialization
# ---------------------------------------------------------------------------
# The @Serializable types in :core (WeatherSnapshot and everything it holds, Location, the favourites
# list) back the on-disk JSON caches that both the app and the widget read. Serialization resolves each
# type's generated `$serializer` through its Companion. The library ships these same rules as consumer
# rules, but the persistent cache is the highest-risk surface here, so pin them explicitly: if R8 ever
# renamed a Companion or dropped a serializer, saved snapshots would fail to parse with no build error.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the Companion of every @Serializable class (that is where serializer() is reached).
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep serializer() on the Companion of each @Serializable class.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializer() on @Serializable objects (singletons), reached through INSTANCE.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# 2. Apache Commons Compress
# ---------------------------------------------------------------------------
# :core uses only the tar reader (net/TarReader.kt, unpacking AEMET's avisos .tar). Commons Compress
# does not ship consumer rules and references many optional compression codecs (xz, zstd, brotli, ...)
# that Aura never pulls in. Without these, R8's missing-class check turns those absent optional deps
# into build errors. We use none of them, so silence the warnings rather than bundle the codecs.
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**

# ---------------------------------------------------------------------------
# 3. Tink / androidx.security-crypto
# ---------------------------------------------------------------------------
# security-crypto encrypts the single stored AEMET key (Layer D). It is backed by Google Tink, which is
# compiled against Error Prone's build-time-only annotations. Those never ship at runtime, so R8's
# missing-class check flags them. They are annotations only (no behaviour), so silencing them is safe;
# this is exactly the list AGP generated in build/.../missing_rules.txt.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
