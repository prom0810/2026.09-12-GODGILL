package com.safewalk.wear.guide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import com.safewalk.wear.presentation.MainActivity
import com.safewalk.wear.settings.Settings
import com.safewalk.wear.sos.SafetyState

/**
 * 경로 안내 중 경보(폰이 판단해서 보냄): 경로 이탈 · 위험구역 접근 · 위험구역 진입.
 *
 * | 종류            | 소리                      | 진동          | 화면                    |
 * |-----------------|---------------------------|---------------|-------------------------|
 * | RISK_APPROACH   | 짧은 비프 2번 (볼륨 80)    | 짧게 2번      | 헤드업 알림             |
 * | RISK_ENTER      | 강한 경보음 5회 ≈5초 (100) | 길게 4번 ≈3.5초 | 전체 화면 알림(화면 켬) |
 * | ROUTE_DEVIATION | 중간 경보음 2회 (90)       | 중간 2번      | 헤드업 알림             |
 *
 * - 소리는 알람 볼륨(STREAM_ALARM)으로 낸다 → 무음·방해 금지 모드·미디어 볼륨 0이어도 들린다. 워치 설정 "소리"가 꺼져 있으면 진동만.
 * - 스피커가 없는 워치도 있으니 진동은 항상 같이 울린다 (진동도 알람 용도로 지정).
 * - SOS 카운트다운·긴급 경보 중이면 무시한다 (SOS가 우선, 헷갈리지 않게 SOS보다 약하게 설계).
 * - 더 센 경보가 울리는 중에 약한 경보가 오면 소리·진동은 생략하고 알림 문구만 바꾼다.
 * - 30초보다 오래된 메시지(블루투스가 늦게 붙어 뒤늦게 도착)는 버린다.
 * - 경로 이탈 반복(이탈이 계속될 때 60초마다 최대 2번)은 폰이 다시 보낸다.
 * 메인 스레드에서 상태를 바꾼다.
 */
object GuideAlertPlayer {
    const val KIND_ROUTE_DEVIATION = "ROUTE_DEVIATION"
    const val KIND_RISK_APPROACH = "RISK_APPROACH"
    const val KIND_RISK_ENTER = "RISK_ENTER"
    const val ACTION_DISMISS = "com.safewalk.wear.action.GUIDE_ALERT_DISMISS"

    private const val MAX_AGE_MS = 30_000L
    private const val CHANNEL = "godgill_guide_alert"
    private const val NOTIFICATION_ID = 3   // AlertNotifier(SOS)는 2

    private class Pattern(
        val priority: Int,
        val toneType: Int,
        val toneMs: Int,
        val toneCount: Int,
        val toneIntervalMs: Long,
        val volume: Int,
        val vibration: LongArray,
        val fullScreen: Boolean,
    ) {
        /** 소리·진동 중 긴 쪽이 끝나는 시점 */
        val durationMs: Long
            get() = maxOf((toneCount - 1) * toneIntervalMs + toneMs, vibration.sum())
    }

    private fun pattern(kind: String): Pattern? = when (kind) {
        KIND_RISK_APPROACH -> Pattern(
            priority = 1, toneType = ToneGenerator.TONE_PROP_BEEP2, toneMs = 400, toneCount = 1, toneIntervalMs = 0,
            volume = 80, vibration = longArrayOf(0, 150, 150, 150), fullScreen = false,
        )
        KIND_ROUTE_DEVIATION -> Pattern(
            priority = 2, toneType = ToneGenerator.TONE_CDMA_ABBR_ALERT, toneMs = 600, toneCount = 2, toneIntervalMs = 900,
            volume = 90, vibration = longArrayOf(0, 400, 200, 400), fullScreen = false,
        )
        KIND_RISK_ENTER -> Pattern(
            priority = 3, toneType = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, toneMs = 700, toneCount = 5, toneIntervalMs = 1000,
            volume = 100, vibration = longArrayOf(0, 600, 250, 600, 250, 600, 250, 600), fullScreen = true,
        )
        else -> null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val token = Any()
    private var tone: ToneGenerator? = null
    /** 알림에 떠 있는 경보 종류 (해제 메시지가 이 종류와 같을 때만 지운다) */
    private var currentKind: String? = null
    /** 소리·진동이 울리고 있는 경보의 우선순위 (0 = 울리는 중 아님) */
    private var playingPriority = 0

    fun play(context: Context, kind: String, title: String, text: String, timeMs: Long) {
        val app = context.applicationContext
        if (timeMs > 0 && System.currentTimeMillis() - timeMs > MAX_AGE_MS) return
        val p = pattern(kind) ?: return
        handler.post {
            if (SafetyState.countdown > 0 || SafetyState.emergencyActive) return@post
            Settings.init(app)
            currentKind = kind
            showNotification(app, kind, title.ifBlank { "안전 알림" }, text, p.fullScreen)
            if (playingPriority > p.priority) return@post   // 더 센 경보가 울리는 중
            stopSound()
            playingPriority = p.priority
            vibrate(app, p.vibration)
            if (Settings.sound) playTone(p)
            handler.postDelayed({ stopSound() }, token, p.durationMs + 300)
        }
    }

    /** 경보 해제. [kind]가 있으면 지금 떠 있는 경보가 그 종류일 때만 끈다 (null이면 무조건) */
    fun stop(context: Context, kind: String? = null) {
        val app = context.applicationContext
        handler.post {
            if (kind != null && kind != currentKind) return@post
            val wasPlaying = playingPriority > 0
            stopSound()
            if (wasPlaying && SafetyState.countdown == 0 && !SafetyState.emergencyActive) {
                runCatching { app.getSystemService(Vibrator::class.java)?.cancel() }
            }
            clearNotification(app)
        }
    }

    /** SOS 카운트다운이 시작될 때 [com.safewalk.wear.sos.SosManager]가 부른다 (메인 스레드). 진동은 SOS가 곧 덮어쓴다 */
    fun stopForSos(context: Context) {
        stopSound()
        clearNotification(context.applicationContext)
    }

    private fun playTone(p: Pattern) {
        val tg = try {
            ToneGenerator(AudioManager.STREAM_ALARM, p.volume)
        } catch (_: Exception) {
            return
        }
        tone = tg
        for (i in 0 until p.toneCount) {
            handler.postDelayed({
                if (tone === tg) runCatching { tg.startTone(p.toneType, p.toneMs) }
            }, token, i * p.toneIntervalMs)
        }
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(token)
        tone?.let { runCatching { it.stopTone(); it.release() } }
        tone = null
        playingPriority = 0
    }

    private fun vibrate(app: Context, pattern: LongArray) {
        val vibrator = app.getSystemService(Vibrator::class.java) ?: return
        val effect = VibrationEffect.createWaveform(pattern, -1)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }
    }

    private fun showNotification(app: Context, kind: String, title: String, text: String, fullScreen: Boolean) {
        try {
            val nm = app.getSystemService(NotificationManager::class.java)
            // 소리·진동은 직접 울리므로 채널은 조용히 (두 번 울리지 않게)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "경로 안내 경보", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            )
            val open = PendingIntent.getActivity(
                app, 10,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val dismiss = PendingIntent.getBroadcast(
                app, 11,
                Intent(app, GuideAlertReceiver::class.java).setAction(ACTION_DISMISS),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = Notification.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setCategory(if (kind == KIND_RISK_ENTER) Notification.CATEGORY_ALARM else Notification.CATEGORY_NAVIGATION)
                .setAutoCancel(true)
                .setContentIntent(open)
                .setDeleteIntent(dismiss)
                .addAction(Notification.Action.Builder(null, "확인", dismiss).build())
            if (fullScreen) builder.setFullScreenIntent(open, true)
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {
            // 알림 권한이 없으면 건너뛴다 (소리·진동은 그대로)
        }
    }

    private fun clearNotification(app: Context) {
        currentKind = null
        runCatching { app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
    }
}
