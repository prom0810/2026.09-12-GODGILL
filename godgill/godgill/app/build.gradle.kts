import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// local.properties에서 카카오 REST API 키를 읽어온다.
// local.properties는 절대 Git에 커밋하지 말 것 (.gitignore에 이미 포함되어 있음).
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}
val kakaoRestApiKey: String = localProperties.getProperty("KAKAO_REST_API_KEY", "")
val vworldApiKey: String = localProperties.getProperty("VWORLD_API_KEY", "")
val dataGoKrApiKey: String = localProperties.getProperty("DATA_GO_KR_API_KEY", "")
val safeMapServiceKey: String = localProperties.getProperty("SAFEMAP_SERVICE_KEY", "")

android {
    namespace = "com.foresto.gatgil"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.foresto.gatgil"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-mvp"

        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestApiKey\"")
        buildConfigField("String", "VWORLD_API_KEY", "\"$vworldApiKey\"")
        buildConfigField("String", "DATA_GO_KR_API_KEY", "\"$dataGoKrApiKey\"")
        buildConfigField("String", "SAFEMAP_SERVICE_KEY", "\"$safeMapServiceKey\"")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 지도: API 키가 필요 없는 OSMDroid 사용 (MVP 단계에서 별도 키 발급 없이 바로 실행 가능)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
