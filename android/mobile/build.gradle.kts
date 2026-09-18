import java.util.Properties

plugins {
    // AGP 9 includes Kotlin support.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}
val kakaoNativeAppKey = localProperties.getProperty("KAKAO_NATIVE_APP_KEY", "").trim()
val kakaoRestApiKey = localProperties.getProperty("KAKAO_REST_API_KEY", "").trim()
val safeMapServiceKey = localProperties.getProperty("SAFEMAP_SERVICE_KEY", "").trim()
fun String.asBuildConfigString() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
require(kakaoRestApiKey.isEmpty() || kakaoRestApiKey.matches(Regex("[a-fA-F0-9]{32}"))) {
    "KAKAO_REST_API_KEY in android/local.properties must be a 32-character REST API Key (without quotes)."
}
require(kakaoNativeAppKey.isEmpty() || kakaoNativeAppKey.matches(Regex("[a-fA-F0-9]{32}"))) {
    "KAKAO_NATIVE_APP_KEY in android/local.properties must be a 32-character Native App Key (without quotes)."
}

android {
    namespace = "com.safewalk"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.safewalk"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", kakaoNativeAppKey.asBuildConfigString())
        buildConfigField("String", "KAKAO_REST_API_KEY", kakaoRestApiKey.asBuildConfigString())
        buildConfigField("String", "SAFEMAP_SERVICE_KEY", safeMapServiceKey.asBuildConfigString())
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("com.kakao.maps.open:android:2.15.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
