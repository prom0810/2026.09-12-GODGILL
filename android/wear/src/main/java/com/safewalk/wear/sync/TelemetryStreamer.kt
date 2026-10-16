package com.safewalk.wear.sync

import android.content.Context
import com.safewalk.wear.settings.Settings
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

/**
 * 센서 원시값(가속도/자이로/심박)을 5초 단위 배치로 묶어 폰으로 보낸다.
 *
 *  배치 = gzip( 헤더 JSON 1줄 + "\n" + CSV 행들 )
 *    헤더: {"v":1,"deviceId","deviceType","seq","startMs","fs","cols","units"}
 *    행  : dt_ms,ax,ay,az,gx,gy,gz,bpm      (dt_ms = startMs 기준 경과 ms, bpm 은 없으면 빈칸)
 *
 *  - Settings.streaming 이 꺼져 있으면 아무 것도 하지 않는다.
 *  - 폰 연결이 끊기면 최근 MAX_QUEUE 개 배치만 메모리에 보관했다가 연결되면 순서대로 보낸다.
 *  - 압축/전송은 별도 스레드에서 처리(센서 콜백은 빨리 반환).
 */
object TelemetryStreamer {
    private const val BATCH_MS = 5_000L
    private const val MAX_QUEUE = 60            // 약 5분 분량 보관
    private const val COLS = "dt,ax,ay,az,gx,gy,gz,bpm"

    private val exec = Executors.newSingleThreadExecutor()
    private val lock = Any()

    private var appContext: Context? = null
    private var rows = StringBuilder()
    private var count = 0
    private var startMs = 0L
    private var seq = 0L

    // exec 스레드에서만 접근
    private val queue = ArrayDeque<ByteArray>()
    private var sending = false

    fun start(context: Context) { appContext = context.applicationContext }

    /** 남은 샘플을 마지막 배치로 내보낸다(서비스 종료 시). */
    fun stop() { flushPartial() }

    fun onSample(
        epochMs: Long, ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float, bpm: Double?,
    ) {
        if (!Settings.streaming) return
        var ready: Triple<String, Long, Long>? = null
        synchronized(lock) {
            if (count == 0) startMs = epochMs
            rows.append(epochMs - startMs).append(',')
                .append(r3(ax)).append(',').append(r3(ay)).append(',').append(r3(az)).append(',')
                .append(r3(gx)).append(',').append(r3(gy)).append(',').append(r3(gz)).append(',')
            if (bpm != null) rows.append(bpm.toInt())
            rows.append('\n')
            count++
            if (epochMs - startMs >= BATCH_MS) {
                ready = Triple(rows.toString(), startMs, seq++)
                rows = StringBuilder(); count = 0
            }
        }
        ready?.let { (body, start, s) -> enqueue(body, start, s) }
    }

    private fun flushPartial() {
        var ready: Triple<String, Long, Long>? = null
        synchronized(lock) {
            if (count > 0) {
                ready = Triple(rows.toString(), startMs, seq++)
                rows = StringBuilder(); count = 0
            }
        }
        ready?.let { (body, start, s) -> enqueue(body, start, s) }
    }

    private fun enqueue(body: String, start: Long, s: Long) {
        exec.execute {
            val header = JSONObject()
                .put("v", 1)
                .put("deviceId", Settings.deviceId)
                .put("deviceType", Settings.deviceType)
                .put("seq", s)
                .put("startMs", start)
                .put("fs", 50)
                .put("cols", COLS)
                .put("units", JSONObject().put("acc", "m/s2").put("gyro", "rad/s").put("bpm", "bpm"))
                .toString()
            val bytes = gzip((header + "\n" + body).toByteArray())
            queue.addLast(bytes)
            while (queue.size > MAX_QUEUE) queue.removeFirst()
            pump()
        }
    }

    /** 대기열 맨 앞부터 하나씩 보낸다. 성공하면 다음 것, 실패하면 다음 배치 때 다시 시도. */
    private fun pump() {
        val ctx = appContext ?: return
        if (sending || queue.isEmpty()) return
        sending = true
        val head = queue.first()
        PhoneLink.send(ctx, Protocol.PATH_TELEMETRY, head) { ok ->
            exec.execute {
                sending = false
                if (ok) { queue.removeFirstOrNull(); pump() }
            }
        }
    }

    private fun r3(v: Float): Float = Math.round(v * 1000f) / 1000f

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }
}
