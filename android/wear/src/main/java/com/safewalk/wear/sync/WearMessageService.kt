package com.safewalk.wear.sync

import android.os.Build
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.safewalk.wear.settings.Settings
import com.safewalk.wear.sos.SosManager
import org.json.JSONObject

/**
 * 폰 -> 워치 메시지 수신.
 *  - PATH_CANCEL       : 카운트다운 원격 취소(폰에서 "괜찮아요" 또는 서버 2차 판정이 오탐)
 *  - PATH_INFO_REQUEST : 워치 정보 요청 → 요청한 폰(sourceNodeId)에게만 PATH_INFO로 답한다 (폰의 워치 등록·연결용)
 */
class WearMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Protocol.PATH_CANCEL -> SosManager.cancel(applicationContext)
            Protocol.PATH_INFO_REQUEST -> replyInfo(event.sourceNodeId)
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
