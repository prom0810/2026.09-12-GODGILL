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
val backendBaseUrl = localProperties.getProperty("BACKEND_BASE_URL", "http://192.168.56.1:8080").trim().trimEnd('/')
fun String.asBuildConfigString() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
require(kakaoRestApiKey.isEmpty() || kakaoRestApiKey.matches(Regex("[a-fA-F0-9]{32}"))) {
    "KAKAO_REST_API_KEY in android/local.properties must be a 32-character REST API Key (without quotes)."
}
require(kakaoNativeAppKey.isEmpty() || kakaoNativeAppKey.matches(Regex("[a-fA-F0-9]{32}"))) {
    "KAKAO_NATIVE_APP_KEY in android/local.properties must be a 32-character Native App Key (without quotes)."
}
require(backendBaseUrl.startsWith("http://") || backendBaseUrl.startsWith("https://")) {
    "BACKEND_BASE_URL in android/local.properties must start with http:// or https://."
}

// google-services.json contains per-developer Firebase project settings and is intentionally
// not committed. Applying the plugin only when the file exists keeps local/CI builds usable.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
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
        buildConfigField("String", "BACKEND_BASE_URL", backendBaseUrl.asBuildConfigString())
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
    sourceSets {
        getByName("test").resources.directories.add("src/main/assets")
    }
}

dependencies {
    implementation("com.kakao.maps.open:android:2.15.2")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")   // 워치 연동
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.activity:activity-compose:1.8.2")
    // Wearable pulls Fragment 1.1.0 transitively, which is too old for Activity Result APIs.
    implementation("androidx.fragment:fragment:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    testImplementation("org.json:json:20260814")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
