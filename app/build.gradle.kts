// Restricts JavaCPP/FFmpeg native binaries to Android ABIs only — without
// this, javacv-platform also pulls Linux/Windows/macOS/iOS desktop binaries
// (multiple extra GB), which would make CI downloads extremely slow and
// bloat local Gradle caches for no reason here. Must be set before the
// dependencies block resolves, so it lives at the very top of the file.
System.setProperty("javacpp.platform", "android-arm,android-arm64,android-x86,android-x86_64")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.bobai.studio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bobai.studio"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Groq API key is injected from GitHub Actions secrets at build time.
        // Never commit a real key into this file.
        val groqApiKey: String = System.getenv("GROQ_API_KEY") ?: ""
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("SIGNING_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val storeFilePath = System.getenv("SIGNING_STORE_FILE")
            signingConfig = if (!storeFilePath.isNullOrBlank()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Media3 (ExoPlayer) for preview + timeline scrubbing
    implementation("androidx.media3:media3-exoplayer:1.4.0")
    implementation("androidx.media3:media3-ui:1.4.0")
    implementation("androidx.media3:media3-common:1.4.0")
    implementation("androidx.media3:media3-transformer:1.4.0")

    // FFmpeg (video render: watermark blur/delogo, fragment replace, trims)
    // NOTE: upstream FFmpegKit (arthenica/ffmpeg-kit) is officially retired
    // and its continuation (FFmpegKitNext) ships source-only, with no
    // prebuilt Maven binaries — so no "install and forget" FFmpegKit
    // coordinate can be trusted long-term in 2026. Instead this project uses
    // org.bytedeco:javacv-platform, part of the actively maintained JavaCPP
    // Presets project (new release as recently as Feb 2026), which bundles
    // real prebuilt FFmpeg native binaries for Android (arm64-v8a,
    // armeabi-v7a, x86, x86_64) and is resolved from plain Maven Central —
    // no third-party republish risk. We call the extracted `ffmpeg` binary
    // the same way FFmpegKit did (a full command-line argument list run via
    // ProcessBuilder), which is why the *.kt call sites in this project stay
    // close to their original FFmpegKit shape.
    implementation("org.bytedeco:javacv-platform:1.5.13")

    // Networking for Groq API (scene analysis / vision)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Local persistence for projects/fragments/beats
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Image loading for gallery thumbnails
    implementation("io.coil-kt:coil-compose:2.6.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
