import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is driven by a gitignored keystore.properties at the repo root, which points at a
// keystore kept OUTSIDE the repo tree (see docs/RELEASE.md, Phase 1). Neither the keystore nor its
// passwords ever enter git. On a clean checkout or CI the file is simply absent, and the release build
// stays unsigned (assembleRelease still runs, it just produces an unsigned APK you can't install as a
// real distribution). Android-note: this is the standard "load a Properties file in the build script"
// pattern, the Kotlin-DSL equivalent of iOS reading signing settings from a local xcconfig.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.mab.aura"
    compileSdk = 36

    defaultConfig {
        // The Play app was registered as com.mab.Aura (capital A), and Play can never rename a package,
        // so the applicationId (the install/store identity) must match that casing exactly. The `namespace`
        // above stays lowercase com.mab.aura: it is the code package where R/BuildConfig and every class
        // live, and AGP keeps the two independent, so nothing in src/ has to move. Relative manifest names
        // like `.MainActivity` still resolve against the lowercase namespace.
        applicationId = "com.mab.Aura"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign only when the local keystore.properties is present; otherwise leave the release
            // unsigned rather than failing the build for someone without the signing secrets.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // The core Material Symbols set only (Warning, chevrons for the cards). Not the large
    // material-icons-extended artifact. Version comes from the Compose BOM above.
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Layer D storage: the encrypted single-secret store (AEMET key) and the DataStore for small app state.
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    // The kotlinx-serialization runtime, to read/write the JSON snapshot cache and the favourites list. The
    // @Serializable types (WeatherSnapshot, Location) are generated in :core; :app needs only the runtime, so
    // no serialization compiler plugin here. :core exposes it as `implementation`, hence it isn't transitive.
    implementation(libs.kotlinx.serialization.json)
    // Coroutines: the repository's coalesced refresh (Mutex, async) and the suspending stores use these
    // directly, so depend explicitly rather than leaning on a transitive copy.
    implementation(libs.kotlinx.coroutines.core)
    // Lifecycle: the "Hoy" screen's ViewModel + StateFlow, and lifecycle-aware Compose state collection.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Jetpack Glance: the Home Screen app widget (widget/*). Its @Composable content is compiled by the
    // Compose plugin already applied above, but renders to RemoteViews rather than Compose, so it reads the
    // shared cache and draws a restricted layout instead of reusing the AuraSky/card Composables.
    implementation(libs.androidx.glance.appwidget)

    // WorkManager: the periodic background refresh (work/*) that keeps the widget's cache current while the
    // app is closed. WorkManager self-initialises via androidx.startup (the default manifest provider), so no
    // custom Configuration or Application subclass is needed.
    implementation(libs.androidx.work.runtime.ktx)

    // Lottie for Compose: plays the Meteocons animated weather icons (res/raw/wx_anim_*.json) in the hourly
    // strip. App-only; the Glance widget can't animate (RemoteViews), so it keeps the static drawables.
    implementation(libs.lottie.compose)

    // JVM unit tests for the app-side pure logic (e.g. AuraSunPath position maths). These run on the
    // local JVM with `./gradlew :app:testDebugUnitTest`; no device or Robolectric needed, since the code
    // under test only touches Compose value classes (Offset) and java.time, not the Android framework.
    testImplementation(libs.junit)
}
