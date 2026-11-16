package com.safewalk.watch

import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/**
 * 워치 -> 폰 메시지 수신. 앱이 꺼져 있어도 시스템이 이 서비스를 깨워 전달한다.
 *
 * 등록된 워치에서 온 데이터만 받는다. 미등록 워치면 데이터를 버리고 등록 유도 알림만 띄운다
 * ([WatchConnectionManager.acceptsData]). 워치 정보 응답(PATH_INFO)과 연결 변화는 연결 관리로 넘긴다.
 */
class WatchMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val app = applicationContext
        if (event.path == Protocol.PATH_INFO) {
            WatchConnectionManager.onWatchInfo(app, event.sourceNodeId, event.data)
            return
        }
        // 센서 배치(gzip)는 본문에서 deviceId를 꺼내지 않고 노드 ID로 워치를 구분한다
        val json = if (event.path == Protocol.PATH_TELEMETRY) null else parse(event.data)
        if (!WatchConnectionManager.acceptsData(app, event.sourceNodeId, json?.optString("deviceId"))) return

        when (event.path) {
            Protocol.PATH_VITALS -> {
                json ?: return
                WatchState.lastBpm = json.optDouble("bpm")
                WatchState.lastBpmTimeMs = json.optLong("timeMs")
            }
            Protocol.PATH_ALERT -> {
                json ?: return
                PhoneLocation.refresh(app)
                val alert = WatchAlert(
                    type = json.optString("type"),
                    auto = json.optBoolean("auto"),
                    timeMs = json.optLong("timeMs"),
                    bpm = if (json.isNull("bpm")) WatchState.lastBpm else json.optDouble("bpm"),
                )
                SafetyAlertNotifier.showAlert(app, alert)
                GuardianNotifier.handler?.invoke(alert, WatchState.lastLat, WatchState.lastLon)
            }
            Protocol.PATH_EVENT -> {
                json ?: return
                PhoneLocation.refresh(app)
                WatchState.lastLat?.let { json.put("lat", it) }
                WatchState.lastLon?.let { json.put("lon", it) }
                if (WatchState.lastLat != null) json.put("accuracy", WatchState.lastAccuracy.toDouble())
                WatchForwarder.onEvent?.invoke(json)
                // 경로 안내 중 워치 자동 감지(낙상·심박) → 폰 팝업 → 무응답이면 SOS
                SensorAlertCoordinator.onWatchEvent(app, json)
            }
            Protocol.PATH_TELEMETRY -> {
                // 심박 이상 SOS 중이면 서버로 전달 (서버는 심박만 읽어 bpm_log에 저장)
                SensorStreamRelay.onTelemetry(app, event.data)
                WatchForwarder.onTelemetry?.invoke(event.data)
            }
        }
    }

    /** 블루투스로 붙어 있는 워치 목록이 바뀜 (연결·끊김) */
    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name != Protocol.CAPABILITY_WATCH) return
        WatchConnectionManager.onReachableWatchesChanged(applicationContext, info.nodes.map { it.id }.toSet())
    }

    private fun parse(b: ByteArray): JSONObject? = runCatching { JSONObject(String(b)) }.getOrNull()
}
