package com.safewalk.watch

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/** 워치 -> 폰 메시지 수신. 앱이 꺼져 있어도 시스템이 이 서비스를 깨워 전달한다. */
class WatchMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Protocol.PATH_VITALS -> {
                val json = parse(event.data) ?: return
                WatchState.lastBpm = json.optDouble("bpm")
                WatchState.lastBpmTimeMs = json.optLong("timeMs")
            }
            Protocol.PATH_ALERT -> {
                val json = parse(event.data) ?: return
                PhoneLocation.refresh(applicationContext)
                val alert = WatchAlert(
                    type = json.optString("type"),
                    auto = json.optBoolean("auto"),
                    timeMs = json.optLong("timeMs"),
                    bpm = if (json.isNull("bpm")) WatchState.lastBpm else json.optDouble("bpm"),
                )
                SafetyAlertNotifier.showAlert(applicationContext, alert)
                GuardianNotifier.handler?.invoke(alert, WatchState.lastLat, WatchState.lastLon)
            }
            Protocol.PATH_EVENT -> {
                val json = parse(event.data) ?: return
                PhoneLocation.refresh(applicationContext)
                WatchState.lastLat?.let { json.put("lat", it) }
                WatchState.lastLon?.let { json.put("lon", it) }
                if (WatchState.lastLat != null) json.put("accuracy", WatchState.lastAccuracy.toDouble())
                WatchForwarder.onEvent?.invoke(json)
            }
            Protocol.PATH_TELEMETRY -> WatchForwarder.onTelemetry?.invoke(event.data)
        }
    }

    private fun parse(b: ByteArray): JSONObject? = runCatching { JSONObject(String(b)) }.getOrNull()
}
