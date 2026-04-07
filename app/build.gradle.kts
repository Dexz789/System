plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.strawberry2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.strawberry2"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md"
            )
        }
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Coroutines (if not already added)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
// Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
// Material Components (ensure you have the latest)
    implementation("com.google.android.material:material:1.10.0")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.0")

    // Firebase BOM - ensures all Firebase libraries use compatible versions
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth-ktx")

    // Optional: For Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.4.0")

    // Firebase Firestore
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Gemini AI
    implementation("com.google.ai.client.generativeai:generativeai:0.1.2")

    implementation ("com.google.firebase:firebase-storage-ktx")

    // For HTTP requests to Pl@net API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
// For JSON parsing (if not already added)
    implementation("org.json:json:20231013")

    implementation("com.sun.mail:android-mail:1.6.7")

    implementation("com.sun.mail:android-activation:1.6.7")

    implementation("io.noties.markwon:core:4.6.2")
}