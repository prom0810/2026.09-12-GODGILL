package com.safewalk.wear.sync

import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.safewalk.wear.sensor.MonitoringService
import com.safewalk.wear.settings.Settings
import com.safewalk.wear.sos.SafetyState
import com.safewalk.wear.sos.SosManager
import org.json.JSONObject

/**
 * 폰 -> 워치 메시지 수신.
 *  - PATH_CANCEL       : 카운트다운 원격 취소(폰에서 "괜찮아요" 또는 서버 2차 판정이 오탐)
 *  - PATH_INFO_REQUEST : 워치 정보 요청 → 요청한 폰(sourceNodeId)에게만 PATH_INFO로 답한다 (폰의 워치 등록·연결용)
 *  - PATH_STREAM       : 센서 스트리밍 on/off (폰이 심박 이상 SOS 동안 요청). on인데 감시 서비스가 꺼져 있으면 켜 본다
 *                        (백그라운드 시작 제한 등으로 실패하면 워치 화면에서 감시를 켜야 한다)
 */
class WearMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Protocol.PATH_CANCEL -> SosManager.cancel(applicationContext)
            Protocol.PATH_INFO_REQUEST -> replyInfo(event.sourceNodeId)
            Protocol.PATH_STREAM -> onStream(event.data)
        }
    }

    private fun onStream(data: ByteArray) {
        Settings.init(applicationContext)
        val on = runCatching { JSONObject(String(data)).optBoolean("on") }.getOrDefault(false)
        TelemetryStreamer.setPhoneRequested(on)
        if (on && !SafetyState.monitoring) {
            runCatching {
                ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, MonitoringService::class.java))
            }.onFailure { SafetyState.lastMessage = "SOS 중: 워치에서 감시를 켜 주세요" }
        }
    }

    private fun replyInfo(nodeId: String) {
        Settings.init(applicationContext)
        val appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        val json = JSONObject()
            .put("deviceId", Settings.deviceId)
            .put("model", Build.MODEL ?: "wear_os")
            .put("appVersion", appVersion)
            .toString()
        runCatching {
            Wearable.getMessageClient(applicationContext).sendMessage(nodeId, Protocol.PATH_INFO, json.toByteArray())
        }
    }
}
