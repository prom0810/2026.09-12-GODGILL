package com.safewalk.wear.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/** 워치 앱 설정. SharedPreferences 에 저장되고 Compose 상태로 노출된다. */
object Settings {
    private var prefs: SharedPreferences? = null

    /** 0 = 둔감, 1 = 표준, 2 = 민감 */
    var sensitivity by mutableStateOf(1)
        private set
    var vitalsAlert by mutableStateOf(true)
        private set
    var sound by mutableStateOf(true)
        private set
    /** 자동 감지(낙상/심박) 후 취소할 수 있는 시간(초) */
    var autoSeconds by mutableStateOf(10)
        private set
    /** 센서 원시 데이터를 폰(→서버)으로 실시간 전송할지. 폰 연동 전까지는 꺼 둔다. */
    var streaming by mutableStateOf(false)
        private set

    /** 기기 식별자(앱 설치 시 1회 생성). 서버가 기기별 기준선/모델 보정에 사용. */
    var deviceId: String = ""
        private set
    /** 기기 종류. 웨어러블이 바뀌어도 서버가 구분할 수 있게 같이 보낸다. */
    val deviceType: String get() = Build.MODEL ?: "wear_os"

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("godgill_settings", Context.MODE_PRIVATE)
        prefs = p
        sensitivity = p.getInt("sensitivity", 1)
        vitalsAlert = p.getBoolean("vitalsAlert", true)
        sound = p.getBoolean("sound", true)
        autoSeconds = p.getInt("autoSeconds", 10)
        streaming = p.getBoolean("streaming", false)
        deviceId = p.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("deviceId", it).apply()
        }
    }

    fun updateSensitivity(v: Int) { sensitivity = v; prefs?.edit()?.putInt("sensitivity", v)?.apply() }
    fun updateVitalsAlert(v: Boolean) { vitalsAlert = v; prefs?.edit()?.putBoolean("vitalsAlert", v)?.apply() }
    fun updateSound(v: Boolean) { sound = v; prefs?.edit()?.putBoolean("sound", v)?.apply() }
    fun updateAutoSeconds(v: Int) { autoSeconds = v; prefs?.edit()?.putInt("autoSeconds", v)?.apply() }
    fun updateStreaming(v: Boolean) { streaming = v; prefs?.edit()?.putBoolean("streaming", v)?.apply() }

    /**
     * 낙상 충격 임계값(m/s²). 표준 35 는 실측 CSV 로 튜닝한 값(낙상 32/32 감지, 일상 오탐 0).
     * 민감/둔감 값은 검증되지 않은 가정치이므로 실기 테스트 후 조정할 것.
     */
    val impactThreshold: Double
        get() = when (sensitivity) { 0 -> 45.0; 2 -> 30.0; else -> 35.0 }

    val sensitivityLabels = listOf("둔감", "표준", "민감")
}
