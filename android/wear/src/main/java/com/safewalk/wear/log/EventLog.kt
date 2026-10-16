package com.safewalk.wear.log

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONObject
import java.io.File

/** outcome: CANCELLED(사용자가 취소) / CONFIRMED(취소 없이 확정) */
data class EventRecord(
    val timeMs: Long,
    val type: String,
    val auto: Boolean,
    val bpm: Double?,
    val outcome: String,
)

/**
 * 이벤트 기록(JSON Lines). 앱 전용 외부 폴더의 events.jsonl 에 쌓이므로
 * Device Explorer / adb pull 로 꺼내 오탐(CANCELLED) 분석에 쓸 수 있다.
 * 모든 호출은 메인 스레드에서 한다(SosManager 가 보장).
 */
object EventLog {
    private var file: File? = null
    val recent = mutableStateListOf<EventRecord>()   // 최신순, 최대 50개

    fun init(context: Context) {
        if (file != null) return
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val f = File(dir, "events.jsonl")
        file = f
        if (f.exists()) {
            val list = try {
                f.readLines().takeLast(50).mapNotNull { parse(it) }.reversed()
            } catch (_: Exception) { emptyList() }
            recent.clear()
            recent.addAll(list)
        }
    }

    fun append(rec: EventRecord) {
        try { file?.appendText(toJson(rec) + "\n") } catch (_: Exception) { }
        recent.add(0, rec)
        while (recent.size > 50) recent.removeAt(recent.size - 1)
    }

    private fun toJson(r: EventRecord): String = JSONObject()
        .put("timeMs", r.timeMs)
        .put("type", r.type)
        .put("auto", r.auto)
        .put("bpm", r.bpm ?: JSONObject.NULL)
        .put("outcome", r.outcome)
        .toString()

    private fun parse(line: String): EventRecord? = try {
        val o = JSONObject(line)
        EventRecord(
            timeMs = o.getLong("timeMs"),
            type = o.getString("type"),
            auto = o.getBoolean("auto"),
            bpm = if (o.isNull("bpm")) null else o.getDouble("bpm"),
            outcome = o.getString("outcome"),
        )
    } catch (_: Exception) { null }
}
