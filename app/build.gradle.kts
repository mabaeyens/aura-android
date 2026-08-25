import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mab.aura"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mab.aura"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    // JVM unit tests for the app-side pure logic (e.g. AuraSunPath position maths). These run on the
    // local JVM with `./gradlew :app:testDebugUnitTest`; no device or Robolectric needed, since the code
    // under test only touches Compose value classes (Offset) and java.time, not the Android framework.
    testImplementation(libs.junit)
}
