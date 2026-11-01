plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.rideauto.assistant"
    compileSdk = 34

    defaultConfig {

        applicationId = "com.rideclicker.proplus"
        minSdk = 26
        targetSdk = 34
        versionCode = 508
        versionName = "5.0.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "appType"

    productFlavors {
        create("client") {
            dimension = "appType"
            applicationId = "com.rideclicker.proplus"
            resValue("string", "app_name", "Ride Clicker Pro+")
            resValue("string", "service_name", "Ride Clicker Pro+ Service")
            resValue("string", "service_desc", "Auto accept service for rides")
        }
        create("admin") {
            dimension = "appType"
            applicationId = "com.rideclicker.proplus.admin"
            resValue("string", "app_name", "Ride Clicker Pro+ Admin")
            resValue("string", "service_name", "Ride Clicker Pro+ Admin Service")
            resValue("string", "service_desc", "Admin management service")
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Compose BOM & UI
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database for Ride History
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Shizuku API for Privileged Touch
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.8")

    // Firebase BOM, Auth, Cloud Firestore & Cloud Messaging (FCM)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Coil Image Loading for Compose
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Razorpay Payment Gateway
    implementation("com.razorpay:checkout:1.6.38")
}

