package com.safewalk.watch

import android.content.Context
import android.util.Log
import com.safewalk.common.network.ApiClient
import com.safewalk.common.network.AuthenticatedApiClient
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 심박 이상 SOS 동안만 워치 센서 배치를 서버로 전달한다 (경로 안내 중 평소에는 보내지 않음).
 * 서버는 배치에서 심박만 읽어 SOS 종료 때 bpm_log(발생·종료 심박)에 저장한다.
 *
 *  - [start] : 심박 이상 SOS가 생성되면 emergencyId 저장 + 워치에 PATH_STREAM {"on":true}
 *  - [onTelemetry] : WatchMessageService가 (연결 중인 워치만) 넘겨준 gzip 본문을 그대로
 *                    POST /api/sensors/telemetry?emergencyId= 로 올린다. 순서대로 1개씩 전송
 *  - 서버 409 (SOS 종료 등) 또는 [stop] : 워치에 {"on":false}, emergencyId 삭제
 *
 * 앱이 꺼져 있어도 WatchMessageService가 깨어나 호출하므로 emergencyId는 SharedPreferences에 둔다.
 */
object SensorStreamRelay {
    private const val TAG = "SensorStreamRelay"
    private const val PREFS = "safewalk_sensor_stream"
    private const val KEY_EMERGENCY_ID = "emergencyId"
    /** 서버가 느릴 때 쌓이는 배치 상한 (약 2.5분). 넘으면 새 배치를 버린다 */
    private const val MAX_PENDING = 30

    private val executor = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)

    /** 센서를 보내는 중인 심박 이상 SOS id, 아니면 null */
    fun activeEmergencyId(context: Context): Long? =
        prefs(context).getLong(KEY_EMERGENCY_ID, 0L).takeIf { it > 0L }

    fun start(context: Context, emergencyId: Long) {
        val app = context.applicationContext
        prefs(app).edit().putLong(KEY_EMERGENCY_ID, emergencyId).apply()
        WatchGateway.send(app, Protocol.PATH_STREAM, JSONObject().put("on", true).toString())
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        val wasActive = activeEmergencyId(app) != null
        prefs(app).edit().remove(KEY_EMERGENCY_ID).apply()
        if (wasActive) WatchGateway.send(app, Protocol.PATH_STREAM, JSONObject().put("on", false).toString())
    }

    fun onTelemetry(context: Context, gzipBatch: ByteArray) {
        val app = context.applicationContext
        val emergencyId = activeEmergencyId(app) ?: return   // SOS 중이 아님 (워치 설정의 streaming이 켜져 있는 경우 등)
        if (pending.incrementAndGet() > MAX_PENDING) {
            pending.decrementAndGet()
            return
        }
        executor.execute {
            try {
                if (activeEmergencyId(app) != emergencyId) return@execute
                AuthenticatedApiClient(app).postBytes("/api/sensors/telemetry?emergencyId=$emergencyId", gzipBatch)
            } catch (e: ApiClient.ApiException) {
                when (e.statusCode) {
                    409 -> if (activeEmergencyId(app) == emergencyId) stop(app)   // SOS 종료됨
                    401 -> stop(app)                                             // 로그아웃됨
                    else -> Log.w(TAG, "센서 배치 전송 실패 (${e.statusCode}): ${e.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "센서 배치 전송 실패: ${e.message}")
            } finally {
                pending.decrementAndGet()
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
