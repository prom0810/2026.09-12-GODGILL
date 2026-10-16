package com.safewalk.watch

import android.content.Context
import com.google.android.gms.wearable.Wearable

/** 폰 -> 워치 메시지 전송. 연결된 워치가 없으면 조용히 무시된다. */
object WatchGateway {
    fun send(context: Context, path: String, payload: String = "") {
        val app = context.applicationContext
        try {
            Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { nodes ->
                nodes.forEach { Wearable.getMessageClient(app).sendMessage(it.id, path, payload.toByteArray()) }
            }
        } catch (_: Exception) {
        }
    }
}
