package com.safewalk.watch

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

/**
 * 경로 안내 중 경보(경로 이탈·위험구역 접근·진입)를 워치로 보낸다 → 워치가 경보음·진동을 울린다.
 *
 * 이 계정에 **연결된 워치에만** 보낸다 ([WatchConnectionManager.activeNodeId]). 연결된 워치가 없거나 전송이 실패해도
 * 폰 알림은 그대로 나가므로 여기서는 기록만 남긴다.
 * timeMs를 같이 보내 블루투스가 늦게 붙어 뒤늦게 도착한 경보는 워치가 버린다 (30초).
 */
object WatchGuideAlert {
    private const val TAG = "WatchGuideAlert"

    fun send(context: Context, kind: String, title: String, text: String) {
        val json = JSONObject()
            .put("kind", kind)
            .put("title", title)
            .put("text", text)
            .put("timeMs", System.currentTimeMillis())
        sendToActive(context, Protocol.PATH_GUIDE_ALERT, json)
    }

    /** [kind]가 null이면 워치에 울리는 안내 경보를 모두 끈다 (안내 종료·로그아웃) */
    fun clear(context: Context, kind: String? = null) {
        val json = JSONObject()
        if (kind != null) json.put("kind", kind)
        sendToActive(context, Protocol.PATH_GUIDE_CLEAR, json)
    }

    private fun sendToActive(context: Context, path: String, json: JSONObject) {
        val app = context.applicationContext
        val nodeId = WatchConnectionManager.activeNodeId(app) ?: return
        try {
            Wearable.getMessageClient(app).sendMessage(nodeId, path, json.toString().toByteArray())
                .addOnFailureListener { Log.w(TAG, "워치 전송 실패 $path: ${it.message}") }
        } catch (e: Exception) {
            Log.w(TAG, "워치 전송 실패 $path: ${e.message}")
        }
    }
}
