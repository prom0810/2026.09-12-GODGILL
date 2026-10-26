package com.safewalk.wear.log

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 낙상 알림이 취소/확정될 때, 그 직전~직후 센서 구간을 CSV로 저장한다.
 *  - 취소(사용자가 "괜찮아요")  -> falsealarm_fall_<시각>.csv   = 오탐 데이터 (학습에서 '낙상 아님')
 *  - 확정(취소 없이 경보)      -> confirmed_fall_<시각>.csv    = 진짜 낙상이거나 취소를 못 한 경우(직접 확인 후 이름 변경)
 * 저장 위치: <앱 외부 files 폴더>/snapshots/   (Device Explorer / adb pull 로 꺼냄)
 * 열: time_ms,label,ax,ay,az,gx,gy,gz,bpm  (녹화 화면 CSV와 같은 형식 -> 기존 Colab 노트북에 그대로 투입 가능)
 * 파일 이름 끝의 숫자를 떼면 라벨이 되므로 'falsealarm_fall' 은 자동으로 '낙상 아님' 클래스가 된다.
 */
object EventSnapshot {
    private const val CAPACITY = 1800            // 50Hz 기준 약 36초
    private const val PRE_MS = 12_000L           // 감지(카운트다운 시작) 12초 전부터
    private const val MAX_FILES = 200

    private val lock = Any()
    private val ts = LongArray(CAPACITY)
    private val v = FloatArray(CAPACITY * 7)     // ax,ay,az,gx,gy,gz,bpm(없으면 NaN)
    private var head = 0
    private var count = 0

    /** 센서 스레드에서 샘플마다 호출. epochMs = 샘플의 실제 시각 */
    fun feed(epochMs: Long, ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, bpm: Double?) {
        synchronized(lock) {
            ts[head] = epochMs
            val o = head * 7
            v[o] = ax; v[o + 1] = ay; v[o + 2] = az; v[o + 3] = gx; v[o + 4] = gy; v[o + 5] = gz
            v[o + 6] = bpm?.toFloat() ?: Float.NaN
            head = (head + 1) % CAPACITY
            if (count < CAPACITY) count++
        }
    }

    /** 낙상 알림의 결과가 정해졌을 때 호출(SosManager). 파일 쓰기는 백그라운드. */
    fun save(context: Context, outcome: String, detectedMs: Long) {
        val label = if (outcome == "CANCELLED") "falsealarm_fall" else "confirmed_fall"
        val from = detectedMs - PRE_MS
        val copyT = ArrayList<Long>()
        val copyV = ArrayList<FloatArray>()
        synchronized(lock) {
            for (k in 0 until count) {
                val i = (head - count + k + CAPACITY) % CAPACITY
                if (ts[i] >= from) {
                    copyT.add(ts[i])
                    copyV.add(v.copyOfRange(i * 7, i * 7 + 7))
                }
            }
        }
        if (copyT.size < 50) return
        val app = context.applicationContext
        Thread {
            try {
                val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "snapshots").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(detectedMs))
                val file = File(dir, "${label}_$stamp.csv")
                BufferedWriter(FileWriter(file)).use { w ->
                    w.write("time_ms,label,ax,ay,az,gx,gy,gz,bpm\n")
                    val t0 = copyT[0]
                    for (n in copyT.indices) {
                        val r = copyV[n]
                        val bpm = if (r[6].isNaN()) "" else r[6].toInt().toString()
                        w.write("${copyT[n] - t0},$label,${r[0]},${r[1]},${r[2]},${r[3]},${r[4]},${r[5]},$bpm\n")
                    }
                }
                prune(dir)
            } catch (_: Exception) { }
        }.start()
    }

    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.name.endsWith(".csv") }?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_FILES) files.take(files.size - MAX_FILES).forEach { it.delete() }
    }
}
