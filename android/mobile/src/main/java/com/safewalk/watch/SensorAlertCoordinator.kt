package com.safewalk.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.safewalk.common.network.ApiClient
import com.safewalk.emergency.EmergencyLocationService
import com.safewalk.emergency.EmergencyMapActivity
import com.safewalk.emergency.EmergencyRepository
import com.safewalk.emergency.EmergencySessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 워치 이상 감지(낙상·심박) → 폰 팝업 → 응답 없으면 자동 SOS. 경로 안내 중에만 동작한다.
 *
 * 판정은 워치가 한다 (FallDetector·VitalsMonitor). 워치는 감지 즉시 카운트다운을 시작하고
 * PATH_EVENT로 PENDING → (CANCELLED | CONFIRMED)를 보낸다. 폰은 같은 카운트다운을 팝업으로 보여 준다.
 *
 *  - "괜찮아요"     : 워치에 PATH_CANCEL → 워치 카운트다운도 취소, SOS 없음
 *  - "도움 요청"     : 바로 SOS
 *  - 워치에서 취소   : CANCELLED 수신 → 팝업 닫기
 *  - 워치 확정       : CONFIRMED 수신 → SOS
 *  - 아무 응답 없음  : 워치 카운트다운(seconds) + 여유 3초 뒤 SOS (확정 메시지가 유실돼도 SOS가 나가게)
 *
 * SOS = POST /api/emergencies {type: FALL | ABNORMAL_HEART_RATE, source: WEARABLE, bpm}. 이후 위치 공유는
 * 기존 SOS와 같다 (EmergencyLocationService). 심박 이상 SOS면 [SensorStreamRelay]로 SOS 동안 워치 센서를 서버에 보내
 * 서버가 종료 시 심박을 알 수 있게 한다 (bpm_log). 낙상은 사건만 기록하고 센서는 보내지 않는다.
 */
object SensorAlertCoordinator {

    sealed interface State {
        data object Idle : State
        /** 카운트다운 중. countdownEndElapsedMs = SystemClock.elapsedRealtime() 기준 워치 카운트다운 끝 */
        data class Counting(val alert: Alert, val countdownEndElapsedMs: Long) : State
        data class Sending(val alert: Alert) : State
        data class Started(val emergencyId: Long, val recovered: Boolean) : State
        data class Failed(val message: String) : State
    }

    /** bpm = 워치가 감지할 때 잰 심박 (없을 수 있음) */
    data class Alert(val watchTimeMs: Long, val watchType: String, val seconds: Int, val bpm: Int? = null) {
        /** 서버 emergency_event.type */
        val serverType: String get() = if (watchType == Protocol.TYPE_FALL) "FALL" else "ABNORMAL_HEART_RATE"
        val title: String get() = when (watchType) {
            Protocol.TYPE_FALL -> "낙상이 감지되었어요"
            Protocol.TYPE_VITALS_HIGH -> "심박수가 매우 높아요"
            Protocol.TYPE_VITALS_LOW -> "심박수가 매우 낮아요"
            Protocol.TYPE_VITALS_SPIKE -> "심박수가 급하게 변했어요"
            else -> "이상 신호가 감지되었어요"
        }
    }

    private val AUTO_TYPES = setOf(Protocol.TYPE_FALL, Protocol.TYPE_VITALS_HIGH, Protocol.TYPE_VITALS_LOW,
        Protocol.TYPE_VITALS_SPIKE)
    private const val GRACE_MS = 3_000L
    private const val DEFAULT_SECONDS = 10
    private const val CHANNEL = "safewalk_sensor_alert"
    private const val NOTIFICATION_ID = 2101

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var timeout: Runnable? = null

    /** WatchMessageService가 PATH_EVENT(연결 중인 워치)를 넘겨준다. 어느 스레드에서 불러도 된다 */
    fun onWatchEvent(context: Context, json: JSONObject) {
        val app = context.applicationContext
        val type = json.optString("type")
        if (!json.optBoolean("auto") || type !in AUTO_TYPES) return
        val timeMs = json.optLong("timeMs")
        val seconds = json.optInt("seconds", DEFAULT_SECONDS).coerceIn(1, 60)
        val bpm = if (json.isNull("bpm")) null else json.optDouble("bpm").takeIf { !it.isNaN() }?.let { Math.round(it).toInt() }
        main.post {
            val current = _state.value
            when (json.optString("outcome")) {
                "PENDING" -> {
                    if (GuidanceStore.activeRouteId(app) == null) return@post   // 안내 중이 아니면 무시
                    if (current is State.Counting || current is State.Sending) return@post
                    begin(app, Alert(timeMs, type, seconds, bpm))
                }
                "CANCELLED" -> if (current is State.Counting && current.alert.watchTimeMs == timeMs) dismiss(app)
                "CONFIRMED" -> when {
                    current is State.Counting && current.alert.watchTimeMs == timeMs -> sendSos(app, current.alert)
                    // PENDING이 유실됐는데 확정만 온 경우
                    current !is State.Counting && current !is State.Sending &&
                        GuidanceStore.activeRouteId(app) != null -> sendSos(app, Alert(timeMs, type, seconds, bpm))
                }
            }
        }
    }

    /** 팝업 "괜찮아요" */
    fun userOk(context: Context) {
        val app = context.applicationContext
        main.post {
            if (_state.value !is State.Counting) return@post
            WatchGateway.send(app, Protocol.PATH_CANCEL)
            dismiss(app)
        }
    }

    /** 팝업 "도움 요청" */
    fun userHelp(context: Context) {
        val app = context.applicationContext
        main.post {
            val current = _state.value
            if (current is State.Counting) sendSos(app, current.alert)
        }
    }

    /** 결과 화면(Started·Failed)을 닫음 */
    fun acknowledge() {
        main.post {
            val current = _state.value
            if (current is State.Started || current is State.Failed) _state.value = State.Idle
        }
    }

    private fun begin(app: Context, alert: Alert) {
        val end = SystemClock.elapsedRealtime() + alert.seconds * 1000L
        _state.value = State.Counting(alert, end)
        val r = Runnable { (_state.value as? State.Counting)?.let { sendSos(app, it.alert) } }
        timeout = r
        main.postDelayed(r, alert.seconds * 1000L + GRACE_MS)
        notifyPopup(app, alert)
        // 앱이 화면에 떠 있으면 바로 팝업 (백그라운드면 시스템이 막으므로 전체 화면 알림이 대신 띄운다)
        runCatching {
            app.startActivity(Intent(app, SensorAlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun dismiss(app: Context) {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
        _state.value = State.Idle
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }

    private fun sendSos(app: Context, alert: Alert) {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
        _state.value = State.Sending(alert)
        io.execute {
            val result = try {
                val started = EmergencyRepository(app)
                    .startAutoDetected(alert.serverType, currentLocation(app), alert.bpm)
                EmergencySessionStore(app).setActive(started.emergencyId)
                // 심박 이상 SOS: 종료 시 심박을 서버가 알 수 있게 SOS 동안 워치 센서 전송
                if (alert.serverType == "ABNORMAL_HEART_RATE") SensorStreamRelay.start(app, started.emergencyId)
                runCatching { EmergencyLocationService.start(app, started.emergencyId) }
                State.Started(started.emergencyId, started.recoveredExisting)
            } catch (e: ApiClient.ApiException) {
                State.Failed((e.message ?: "SOS를 보내지 못했어요.") + " 지도 화면의 SOS 버튼을 눌러 주세요.")
            } catch (e: Exception) {
                State.Failed("SOS를 보내지 못했어요. 네트워크를 확인하고 지도 화면의 SOS 버튼을 눌러 주세요.")
            }
            main.post {
                _state.value = result
                notifyResult(app, result)
            }
        }
    }

    /** 현재 위치 (최대 5초), 실패하면 마지막으로 알려진 위치, 권한이 없으면 null */
    private fun currentLocation(app: Context): Location? {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        val current: Location? = try {
            Tasks.await(
                LocationServices.getFusedLocationProviderClient(app)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null),
                5, TimeUnit.SECONDS,
            )
        } catch (_: Exception) {
            null
        }
        return current ?: lastKnown(app)
    }

    private fun lastKnown(app: Context): Location? {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun notifyPopup(app: Context, alert: Alert) {
        val open = PendingIntent.getActivity(
            app, NOTIFICATION_ID,
            Intent(app, SensorAlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(app, NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 ${alert.title}")
            .setContentText("${alert.seconds}초 안에 응답이 없으면 보호자에게 SOS를 보내요")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true))
    }

    private fun notifyResult(app: Context, result: State) {
        val (title, text, intent) = when (result) {
            is State.Started -> Triple(
                "SOS가 시작됐어요", "보호자에게 알리고 위치를 공유하고 있어요.",
                Intent(app, EmergencyMapActivity::class.java)
                    .putExtra(EmergencyMapActivity.EXTRA_EMERGENCY_ID, result.emergencyId),
            )
            is State.Failed -> Triple("SOS를 보내지 못했어요", result.message,
                Intent(app, SensorAlertActivity::class.java))
            else -> return
        }
        val open = PendingIntent.getActivity(app, NOTIFICATION_ID + 1, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(app, NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open))
    }

    private fun post(app: Context, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= 26) {
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "워치 이상 감지", NotificationManager.IMPORTANCE_HIGH))
        }
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, builder.build())
    }
}
