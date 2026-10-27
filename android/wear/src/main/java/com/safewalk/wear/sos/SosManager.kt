package com.safewalk.wear.sos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.safewalk.wear.log.EventLog
import com.safewalk.wear.log.EventRecord
import com.safewalk.wear.settings.Settings

/**
 * 긴급 요청 관리(워치 단독).
 *
 *  start()  -> 취소 가능한 카운트다운 (수동 SOS 5초 / 자동 감지 설정값, 기본 10초)
 *  cancel() -> "괜찮아요": 기록(CANCELLED)만 남기고 종료  ← 오탐 분석에 쓰인다
 *  카운트다운 종료 -> confirm(): 기록(CONFIRMED) + 경보음 + 반복 진동
 *  취소/확정 모두 AlertSink 로 전달(폰/서버 전송은 sync/PhoneAlertSink)
 *  dismissEmergency() -> 경보 해제
 *
 * 모든 상태 변경은 메인 스레드 Handler 에서 처리한다(어느 스레드에서 불러도 안전).
 * 자동 전화는 하지 않는다. 112 는 사용자가 화면 버튼으로 직접 건다.
 */
object SosManager {
    const val MANUAL_SECONDS = 5

    private val handler = Handler(Looper.getMainLooper())
    private var type = ""
    private var auto = false
    private var bpmAtStart: Double? = null
    private var tick: Runnable? = null
    private var startMs = 0L            // 감지(카운트다운 시작) 시각. 모든 단계의 이벤트가 같은 timeMs 를 쓴다

    fun start(
        context: Context,
        type: String,
        reason: String,
        auto: Boolean,
        seconds: Int = if (auto) Settings.autoSeconds else MANUAL_SECONDS,
    ) {
        val app = context.applicationContext
        handler.post {
            if (SafetyState.countdown > 0 || SafetyState.emergencyActive) return@post
            this.type = type
            this.auto = auto
            this.bpmAtStart = SafetyState.bpm
            this.startMs = System.currentTimeMillis()
            SafetyState.countdownReason = reason
            SafetyState.countdown = seconds
            SafetyState.lastMessage = ""
            // 서버 2차 판정용 'PENDING' (기록 파일에는 남기지 않음). 폰이 없으면 조용히 무시
            AlertSinks.dispatch(app, EventRecord(startMs, type, auto, bpmAtStart, "PENDING"))
            vibrate(app, 300)
            AlertNotifier.show(app, "긴급 확인", "$reason · ${seconds}초 안에 취소하세요")

            val r = object : Runnable {
                override fun run() {
                    val left = SafetyState.countdown - 1
                    if (left <= 0) {
                        SafetyState.countdown = 0
                        confirm(app)
                    } else {
                        SafetyState.countdown = left
                        vibrate(app, 120)
                        handler.postDelayed(this, 1000)
                    }
                }
            }
            tick = r
            handler.postDelayed(r, 1000)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        handler.post {
            tick?.let { handler.removeCallbacks(it) }
            tick = null
            if (SafetyState.countdown > 0) {
                val rec = record("CANCELLED")
                if (type == EventType.FALL) com.safewalk.wear.log.EventSnapshot.save(app, "CANCELLED", startMs)   // 오탐 센서 구간 저장
                SafetyState.lastMessage = "취소되었습니다"
                AlertSinks.dispatch(app, rec)        // 오탐 라벨도 서버로 보낸다
            }
            SafetyState.countdown = 0
            AlertNotifier.clear(app)
        }
    }

    fun dismissEmergency(context: Context) {
        val app = context.applicationContext
        handler.post {
            AlarmPlayer.stop()
            app.getSystemService(Vibrator::class.java)?.cancel()
            SafetyState.emergencyActive = false
            SafetyState.lastMessage = "해제되었습니다"
            AlertNotifier.clear(app)
        }
    }

    private fun confirm(app: Context) {
        val rec = record("CONFIRMED")
        if (type == EventType.FALL) com.safewalk.wear.log.EventSnapshot.save(app, "CONFIRMED", startMs)
        SafetyState.emergencyReason = SafetyState.countdownReason
        SafetyState.emergencyTimeMs = rec.timeMs
        SafetyState.emergencyActive = true
        AlarmPlayer.start()
        app.getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 700, 300), 0)   // 해제할 때까지 반복
        )
        AlertNotifier.show(app, "긴급 상황", SafetyState.emergencyReason)
        AlertSinks.dispatch(app, rec)
    }

    private fun record(outcome: String): EventRecord {
        val rec = EventRecord(startMs, type, auto, bpmAtStart, outcome)
        EventLog.append(rec)
        return rec
    }

    private fun vibrate(context: Context, ms: Long) {
        context.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
