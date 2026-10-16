package com.safewalk.wear.sos

import android.content.Context
import com.safewalk.wear.log.EventRecord
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 이벤트(낙상/SOS/심박 이상)가 끝났을 때 호출되는 확장점.
 *  - rec.outcome == "CONFIRMED": 취소 없이 확정된 긴급 상황
 *  - rec.outcome == "CANCELLED": 사용자가 "괜찮아요"로 취소 (= 오탐 라벨, 서버 재학습에 중요)
 * 폰(Data Layer) 전송은 sync/PhoneAlertSink 가 이 인터페이스로 붙는다.
 */
interface AlertSink {
    fun onEvent(context: Context, rec: EventRecord)
}

object AlertSinks {
    private val sinks = CopyOnWriteArrayList<AlertSink>()

    fun register(sink: AlertSink) { sinks.addIfAbsent(sink) }
    fun unregister(sink: AlertSink) { sinks.remove(sink) }

    fun dispatch(context: Context, rec: EventRecord) {
        sinks.forEach { s -> try { s.onEvent(context, rec) } catch (_: Exception) { } }
    }
}
