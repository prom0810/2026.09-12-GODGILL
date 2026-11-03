package com.safewalk.emergency

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.safewalk.auth.TokenStore
import com.safewalk.common.network.ApiClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EmergencySseClient(
    context: Context,
    private val emergencyId: Long,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onLocation(location: EmergencyLocation)
        fun onLocationStale(lastRecordedAt: String)
        fun onStatus(status: String, resolvedAt: String)
        fun onConnectionError(message: String, retrying: Boolean)
        fun onAuthenticationExpired()
    }

    private val appContext = context.applicationContext
    private val tokenStore = TokenStore(appContext)
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var eventSource: EventSource? = null
    private var stopped = true
    private var terminalStatusReceived = false
    private var reconnectAttempt = 0

    private val reconnect = Runnable { open() }

    fun start() {
        if (!stopped) return
        stopped = false
        terminalStatusReceived = false
        reconnectAttempt = 0
        open()
    }

    fun stop() {
        stopped = true
        handler.removeCallbacks(reconnect)
        eventSource?.cancel()
        eventSource = null
    }

    private fun open() {
        if (stopped || terminalStatusReceived) return
        val token = tokenStore.accessToken()
        if (token.isNullOrBlank()) {
            listener.onAuthenticationExpired()
            stop()
            return
        }
        val request = Request.Builder()
            .url("${ApiClient.baseUrl}/api/emergencies/$emergencyId/stream")
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("ngrok-skip-browser-warning", "true")
            .build()
        eventSource = EventSources.createFactory(client).newEventSource(request, sourceListener)
    }

    private val sourceListener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            reconnectAttempt = 0
            handler.post { if (!stopped) listener.onConnected() }
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            runCatching {
                val json = JSONObject(data)
                when (type) {
                    "location" -> listener.onLocation(json.toEmergencyLocation())
                    "location_stale" -> listener.onLocationStale(json.optString("lastRecordedAt"))
                    "status" -> {
                        terminalStatusReceived = true
                        listener.onStatus(json.optString("status"), json.optString("resolvedAt"))
                        stop()
                    }
                }
            }.onFailure {
                handler.post { listener.onConnectionError("위치 이벤트를 읽지 못했습니다.", retrying = true) }
            }
        }

        override fun onClosed(eventSource: EventSource) {
            if (!terminalStatusReceived) scheduleReconnect("실시간 연결이 종료되었습니다.")
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            if (stopped || terminalStatusReceived) return
            if (response?.code == 401) {
                tokenStore.clear()
                handler.post { listener.onAuthenticationExpired() }
                stop()
                return
            }
            val message = when (response?.code) {
                403, 404 -> "이 SOS의 위치를 볼 권한이 없거나 SOS를 찾을 수 없습니다."
                else -> "실시간 위치 연결이 끊겼습니다."
            }
            scheduleReconnect(message, retry = response?.code !in setOf(403, 404))
        }
    }

    private fun scheduleReconnect(message: String, retry: Boolean = true) {
        handler.post {
            if (stopped || terminalStatusReceived) return@post
            if (!retry) {
                listener.onConnectionError(message, retrying = false)
                stop()
                return@post
            }
            val delays = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
            val delay = delays[reconnectAttempt.coerceAtMost(delays.lastIndex)]
            reconnectAttempt++
            listener.onConnectionError(message, retrying = true)
            handler.removeCallbacks(reconnect)
            handler.postDelayed(reconnect, delay)
        }
    }
}
