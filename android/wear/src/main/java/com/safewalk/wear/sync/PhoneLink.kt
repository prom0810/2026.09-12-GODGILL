package com.safewalk.wear.sync

import android.content.Context
import com.google.android.gms.wearable.Wearable

/** 연결된 폰(노드)으로 메시지 전송. 폰이 없거나 연결이 끊겨도 예외 없이 onResult(false). */
object PhoneLink {
    /** onResult(true) = 1개 이상의 노드로 전송 요청 성공 (콜백은 메인 스레드) */
    fun send(context: Context, path: String, payload: ByteArray, onResult: ((Boolean) -> Unit)? = null) {
        val app = context.applicationContext
        try {
            Wearable.getNodeClient(app).connectedNodes
                .addOnSuccessListener { nodes ->
                    if (nodes.isEmpty()) { onResult?.invoke(false); return@addOnSuccessListener }
                    nodes.forEach { Wearable.getMessageClient(app).sendMessage(it.id, path, payload) }
                    onResult?.invoke(true)
                }
                .addOnFailureListener { onResult?.invoke(false) }
        } catch (_: Exception) {
            onResult?.invoke(false)
        }
    }

    fun send(context: Context, path: String, payload: String, onResult: ((Boolean) -> Unit)? = null) {
        send(context, path, payload.toByteArray(), onResult)
    }
}
