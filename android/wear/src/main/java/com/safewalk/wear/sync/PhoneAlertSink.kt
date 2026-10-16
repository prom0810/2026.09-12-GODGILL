package com.safewalk.wear.sync

import android.content.Context
import com.safewalk.wear.log.EventRecord
import com.safewalk.wear.settings.Settings
import com.safewalk.wear.sos.AlertSink
import com.safewalk.wear.sos.AlertSinks
import org.json.JSONObject

/**
 * 이벤트를 폰으로 전달한다.
 *  - PATH_EVENT : 취소/확정 모두. 서버가 "이 낙상 후보는 오탐이었다"를 라벨로 학습에 쓴다.
 *  - PATH_ALERT : 확정(CONFIRMED)만. 기존 폰 WatchGateway(보호자 알림) 호환.
 * 폰이 연결돼 있지 않으면 조용히 실패한다(워치 단독 동작은 그대로).
 */
object PhoneAlertSink : AlertSink {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        AlertSinks.register(this)
    }

    override fun onEvent(context: Context, rec: EventRecord) {
        val json = JSONObject()
            .put("deviceId", Settings.deviceId)
            .put("deviceType", Settings.deviceType)
            .put("type", rec.type)
            .put("auto", rec.auto)
            .put("timeMs", rec.timeMs)
            .put("bpm", rec.bpm ?: JSONObject.NULL)
            .put("outcome", rec.outcome)
            .toString()
        PhoneLink.send(context, Protocol.PATH_EVENT, json)
        if (rec.outcome == "CONFIRMED") PhoneLink.send(context, Protocol.PATH_ALERT, json)
    }
}
