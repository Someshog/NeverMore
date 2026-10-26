plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nevermore.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nevermore.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }
    sourceSets.getByName("main").jniLibs.srcDirs("src/main/jniLibs")
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-material:1.0.0")
}

