plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zonik.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zonik.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Wear Compose
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    // Horologist
    implementation(libs.horologist.compose.layout)
    implementation(libs.horologist.compose.material)

    // Compose foundation (for Compose runtime, etc.)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Compose Material Icons
    implementation(libs.compose.material.icons)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)

    // Media3 Session (client only — connects to phone's service)
    implementation(libs.media3.session)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Image Loading
    implementation(libs.coil.compose)

    // Wear Input (rotary)
    implementation(libs.wear.input)

    // Tiles
    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.material)
    implementation(libs.horologist.tiles)

    // Complications
    implementation(libs.wear.watchface.complications)
}
