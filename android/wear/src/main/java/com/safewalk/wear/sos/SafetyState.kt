package com.safewalk.wear.sos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 이벤트 종류. 나중에 폰/서버 연동 시 그대로 전송 규약으로 쓸 수 있다. */
object EventType {
    const val SOS_MANUAL = "SOS_MANUAL"
    const val FALL = "FALL"
    const val VITALS_HIGH = "VITALS_HIGH"
    const val VITALS_LOW = "VITALS_LOW"
    const val VITALS_SPIKE = "VITALS_SPIKE"
    const val TEST = "TEST"

    fun label(type: String) = when (type) {
        SOS_MANUAL -> "SOS 요청"
        FALL -> "낙상 감지"
        VITALS_HIGH -> "심박 높음"
        VITALS_LOW -> "심박 낮음"
        VITALS_SPIKE -> "심박 급변"
        TEST -> "테스트"
        else -> type
    }
}

/** UI 와 서비스가 공유하는 상태 (같은 프로세스). */
object SafetyState {
    var bpm by mutableStateOf<Double?>(null)
    var hrStatus by mutableStateOf("대기")
    var monitoring by mutableStateOf(false)

    var countdown by mutableStateOf(0)            // 0 = 카운트다운 없음
    var countdownReason by mutableStateOf("")

    var emergencyActive by mutableStateOf(false)  // 카운트다운이 끝나 확정된 긴급 상황
    var emergencyReason by mutableStateOf("")
    var emergencyTimeMs by mutableStateOf(0L)

    var lastMessage by mutableStateOf("")
}
