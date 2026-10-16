package com.safewalk.wear.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.safewalk.wear.falldetect.FallDetector
import com.safewalk.wear.presentation.MainActivity
import com.safewalk.wear.settings.Settings
import com.safewalk.wear.sos.EventType
import com.safewalk.wear.sos.SafetyState
import com.safewalk.wear.sos.SosManager
import com.safewalk.wear.sync.TelemetryStreamer
import com.safewalk.wear.vitals.VitalsMonitor

/** 화면이 꺼져도 낙상/심박 감시를 유지하는 포그라운드 서비스(health 타입). 폰 없이 단독 동작. */
class MonitoringService : Service() {

    private var fall = buildFall()
    private var fallImpact = Settings.impactThreshold
    private val vitals = VitalsMonitor()
    private var collector: SensorCollector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private fun buildFall() = FallDetector(impactThreshold = Settings.impactThreshold)

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        try {
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } catch (e: Exception) {
            // 심박 권한이 없는 상태에서 서비스가 재시작된 경우 등
            SafetyState.lastMessage = "감시를 시작할 수 없습니다(권한 확인)"
            stopSelf()
            return
        }

        // 배터리를 많이 쓴다(화면 꺼진 상태에서도 가속도 상시 수집). 12시간 상한.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "godgill:monitor")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        TelemetryStreamer.start(this)
        collector = SensorCollector(
            this, this::onAccel, this::onHeartRate,
            { status ->
                SafetyState.hrStatus = status
                // 미착용/사용 불가일 때 오래된 심박값이 남아 판정·전송에 쓰이지 않게 비운다
                if (status.contains("미착용") || status.contains("불가")) SafetyState.bpm = null
            },
            this::onSample,
        ).also { it.start() }
        SafetyState.monitoring = true
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "안전 모니터링", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("갓길 안전 모니터링 중")
            .setContentText("낙상 · 심박 감지")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun onAccel(t: Long, x: Float, y: Float, z: Float) {
        // 설정에서 감도를 바꾸면 감지기를 새로 만든다
        if (Settings.impactThreshold != fallImpact) {
            fallImpact = Settings.impactThreshold
            fall = buildFall()
        }
        fall.feed(t, x, y, z)?.let {
            SosManager.start(this, EventType.FALL, "낙상이 감지되었습니다", auto = true)
        }
    }

    /** 폰/서버 전송용 원시 샘플(설정에서 켠 경우에만 동작). */
    private fun onSample(t: Long, ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        TelemetryStreamer.onSample(t, ax, ay, az, gx, gy, gz, SafetyState.bpm)
    }

    private fun onHeartRate(t: Long, bpm: Double) {
        SafetyState.bpm = bpm
        if (!Settings.vitalsAlert) return
        vitals.feed(t, bpm)?.let { ev ->
            val (type, msg) = when (ev.kind) {
                VitalsMonitor.Kind.HIGH -> EventType.VITALS_HIGH to "심박수가 매우 높습니다"
                VitalsMonitor.Kind.LOW -> EventType.VITALS_LOW to "심박수가 매우 낮습니다"
                VitalsMonitor.Kind.SPIKE -> EventType.VITALS_SPIKE to "심박수가 급변했습니다"
            }
            SosManager.start(this, type, msg, auto = true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        collector?.stop()
        TelemetryStreamer.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        SafetyState.monitoring = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { private const val CHANNEL = "godgill_monitor" }
}
