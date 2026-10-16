package com.safewalk.wear.sos

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.safewalk.wear.settings.Settings

/** 긴급 상황 확정 시 반복 경보음(알람 볼륨). 해제하면 멈춘다. 메인 스레드에서 호출. */
object AlarmPlayer {
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private var running = false

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            try { tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700) } catch (_: Exception) { }
            handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        if (!Settings.sound || running) return
        try {
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (_: Exception) { tone = null; return }
        running = true
        handler.post(loop)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(loop)
        try { tone?.stopTone(); tone?.release() } catch (_: Exception) { }
        tone = null
    }
}
