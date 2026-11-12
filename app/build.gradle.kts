plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.thoragent.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thoragent.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "0.8.0-thor-mobile"
    }

    signingConfigs {
        // Ditandatangani debug key: distribusi sideload tanpa Play (sesuai preferensi).
        getByName("debug")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            // R8 memangkas Compose/OkHttp/Room yang tak terpakai -> APK ringan.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
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
    packaging {
        resources.excludes += "META-INF/*"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // Compose (BOM) — TANPA material-icons-extended (puluhan MB di DEX debug;
    // ikon kustom mendiami ui/icons/ThorIcons.kt)
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigasi
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // DocumentFile untuk penjelajahan SAF (CWD selector)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Room (persistensi sesi/chat/provider)
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // DataStore (preferensi)
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // WorkManager (keep-alive agen latar belakang)
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    // OkHttp (SSE streaming chat completions + unduhan repo GitHub)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // kotlinx.serialization (tool schema + katalog skill)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // libsu (akses root on-demand: Shell.Builder, isAppGrantedSu, CallbackList)
    implementation("com.github.topjohnwu.libsu:core:6.0.0")

    // Unit test (JVM): SSE parser + loop agen
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
