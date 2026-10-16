package com.safewalk.watch

import org.json.JSONObject

/** 워치(com.safewalk.wear.sync.Protocol)와 값이 동일해야 한다. */
object Protocol {
    const val PATH_ALERT = "/safewalk/alert"          // 워치 -> 폰: 확정된 긴급 상황
    const val PATH_VITALS = "/safewalk/vitals"        // 워치 -> 폰: 주기적 심박
    const val PATH_EVENT = "/safewalk/event"          // 워치 -> 폰: 이벤트(PENDING/CANCELLED/CONFIRMED)
    const val PATH_TELEMETRY = "/safewalk/telemetry"  // 워치 -> 폰: gzip 센서 배치
    const val PATH_CANCEL = "/safewalk/cancel"        // 폰 -> 워치: 카운트다운 취소

    const val TYPE_SOS_MANUAL = "SOS_MANUAL"
    const val TYPE_FALL = "FALL"
    const val TYPE_VITALS_HIGH = "VITALS_HIGH"
    const val TYPE_VITALS_LOW = "VITALS_LOW"
    const val TYPE_VITALS_SPIKE = "VITALS_SPIKE"
    const val TYPE_TEST = "TEST"
}

/** 워치에서 받은 최신 상태. 알림에 위치·심박을 붙이는 데 쓴다. */
object WatchState {
    @Volatile var lastBpm: Double? = null
    @Volatile var lastBpmTimeMs: Long = 0
    @Volatile var lastLat: Double? = null
    @Volatile var lastLon: Double? = null
    @Volatile var lastAccuracy: Float = 0f
}

data class WatchAlert(val type: String, val auto: Boolean, val timeMs: Long, val bpm: Double?)

/** 보호자 알림 연동 지점. 백엔드 /api/emergency/sos 가 구현되면 handler 에서 호출하면 된다. */
object GuardianNotifier {
    @Volatile var handler: ((WatchAlert, Double?, Double?) -> Unit)? = null
}

/** 센서 배치/이벤트를 서버로 올리는 쪽(선택 모듈)이 연결하는 훅. 설정하지 않으면 무시된다. */
object WatchForwarder {
    @Volatile var onTelemetry: ((ByteArray) -> Unit)? = null
    @Volatile var onEvent: ((JSONObject) -> Unit)? = null
}
