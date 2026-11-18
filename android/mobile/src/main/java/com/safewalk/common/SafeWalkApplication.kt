package com.safewalk.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Application
import android.os.Build
import com.kakao.vectormap.KakaoMapSdk
import com.safewalk.BuildConfig
import com.safewalk.notification.FcmTokenManager

class SafeWalkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    "safewalk_alerts",
                    "안전 및 보호자 알림",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        FcmTokenManager.sync(this)
        // 위험구역: 캐시를 바로 반영하고(경로 점수·안내 중 알림), 서버 값은 백그라운드에서 갱신 (6시간마다)
        com.safewalk.map.model.RiskZoneRepository.loadCached(this)
        Thread { com.safewalk.map.model.RiskZoneRepository.refresh(this) }.start()
    }
}
