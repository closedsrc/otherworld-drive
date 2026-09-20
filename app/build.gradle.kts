import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing. The keystore lives OUTSIDE the repository (default
// ~/.dfc-keystore/release.keystore) and its credentials live in the gitignored
// local.properties, so a clone never contains signing material and a build
// machine never reads secrets from version control. With no keystore
// configured, release falls back to the debug key: assembleRelease then
// produces an installable artifact for local testing instead of failing at
// the sign step. Distribution builds require the real keystore — that is the
// point of the fallback being marked as debug.
val keystoreProps: Properties = run {
    val p = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { p.load(it) }
    p
}

val keystorePath = keystoreProps.getProperty("dfc.keystore.path") ?: ""
val keystoreStore = keystoreProps.getProperty("dfc.keystore.storePassword") ?: ""
val keystoreAlias = keystoreProps.getProperty("dfc.keystore.alias") ?: ""
val keystoreKey = keystoreProps.getProperty("dfc.keystore.keyPassword") ?: ""
val hasReleaseKeystore = keystorePath.isNotEmpty() && keystoreStore.isNotEmpty() &&
    keystoreAlias.isNotEmpty() && keystoreKey.isNotEmpty() && File(keystorePath).exists()

android {
    namespace = "com.dfc.mobile"
    compileSdk = 34

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = File(keystorePath)
                storePassword = keystoreStore
                keyAlias = keystoreAlias
                keyPassword = keystoreKey
            }
        }
    }

    defaultConfig {
        applicationId = "com.dfc.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "3.4"

        // No credential is baked in. The device registers itself with the
        // server (password exchange, once) and stores its own token in
        // EncryptedSharedPreferences — see Prefs and SetupScreen.
    }

    buildTypes {
        release {
            // R8 shrinks the app to a few MB; the keep rules below preserve
            // Room's generated code and WorkManager's reflection entry points.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "META-INF/{AL2.0,LGPL2.1}"
    }
}

// Export Room's schema JSON on every build: migration tests read it, and a
// schema that exists only in the generated code cannot be versioned or diffed.
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // Engine deps unchanged: the backup engine, Room DB, and API layer are
    // untouched by the Compose rebuild.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    // lifecycle-runtime-compose is deliberately absent: its lifecycle-aware
    // collectors read LocalLifecycleOwner from the 2.8 namespace, which the
    // Compose 1.6.8 that this Kotlin version pins does not provide.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // The device token lives in EncryptedSharedPreferences (Android Keystore).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Biometric app lock.
    implementation("androidx.biometric:biometric:1.1.0")

    // UI: single-activity Compose. No image-loading framework; ThumbLoader
    // already streams bytes and is reused behind a Compose adapter.
    implementation("androidx.activity:activity-compose:1.9.0")
    // Typed routes (2.8+) are what the navigation graph is built on: string
    // routes would be checked at runtime instead of compile time.
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    val compose = "1.6.8"
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.foundation:foundation:$compose")
    implementation("androidx.compose.animation:animation:$compose")
    implementation("androidx.compose.material:material-icons-core:$compose")
    implementation("androidx.compose.material:material-icons-extended:$compose")
    implementation("androidx.compose.material3:material3:1.2.1")
}
