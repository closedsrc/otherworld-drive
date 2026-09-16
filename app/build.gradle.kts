plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.dfc.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dfc.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"
    }

    buildTypes {
        release {
            // R8 shrinks the app to a few MB; the keep rules below preserve
            // Room's generated code and WorkManager's reflection entry points.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "META-INF/{AL2.0,LGPL2.1}"
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

    // UI: single-activity Compose. No image-loading framework; ThumbLoader
    // already streams bytes and is reused behind a Compose adapter.
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    val compose = "1.6.8"
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.foundation:foundation:$compose")
    implementation("androidx.compose.animation:animation:$compose")
    implementation("androidx.compose.material:material-icons-core:$compose")
    implementation("androidx.compose.material:material-icons-extended:$compose")
    implementation("androidx.compose.material3:material3:1.2.1")
}
