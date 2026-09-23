package com.safewalk.common

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import com.safewalk.BuildConfig

class SafeWalkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
    }
}
