plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Riconoscimento dei volti (OpenCV): spento di default per avere un APK leggero e una build più sicura.
// Per accenderlo: gradle assembleDebug -PwithFaces=true  (oppure l'opzione «con_volti» del workflow GitHub).
val withFaces = (project.findProperty("withFaces") as String?) == "true"

android {
    namespace = "com.nebula.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nebula.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        if (withFaces) {
            ndk { abiFilters += listOf("arm64-v8a") }
        }
    }

    signingConfigs {
        // Firma di debug stabile (file incluso nel progetto): l'impronta SHA-1 non cambia a ogni build,
        // così l'accesso a Google resta valido.
        getByName("debug") {
            val ks = file("debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    sourceSets.getByName("main").java.srcDir(if (withFaces) "src/faces-on/java" else "src/faces-off/java")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.osmdroid:osmdroid-android:6.1.18")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.mlkit:text-recognition:16.0.0")

    if (withFaces) {
        implementation("org.opencv:opencv:4.9.0")
    }
}
