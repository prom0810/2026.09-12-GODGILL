package com.safewalk.wear.falldetect

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 가속도(중력 포함, m/s²) 기반 낙상 감지. Android 의존성 없음(단위 테스트 가능).
 * 충격(큰 가속도 스파이크) -> 1.5초 뒤부터 2초간 거의 움직임 없음 => 낙상.
 * 자유낙하 구간이 직전에 있었는지(freeFallSeen)는 신뢰도 참고용으로만 반환.
 */
class FallDetector(
    // 아래 기본값은 실측 CSV(낙상 32개 + hit/shake/clap/run/walk/stair/sit/stand)를 재생해 튜닝한 값.
    // 낙상 감지 32/32, 일상 동작 오탐 0회 (fall_detector_replay.ipynb 결과)
    private val impactThreshold: Double = 35.0,     // 약 3.6g (이전 28.0: hit/shake 오탐 100%)
    private val freeFallThreshold: Double = 4.0,
    private val stillDeviationMax: Double = 1.5,    // |mag-g| 평균 허용치 (이전 2.5)
    private val stillSpikeMax: Double = 10.0,       // 이전 6.0
    private val stillDelayMs: Long = 1500,          // 이전 1000: 넘어진 직후 흔들림이 가라앉을 때까지 대기
    private val stillWindowMs: Long = 5000,         // 이전 2000. 정지를 오래 요구해 무릎 치기 같은 '툭 치고 잠깐 정지' 오탐을 줄임(낙상 32/32, 일상 오탐 0 유지)
    private val cooldownMs: Long = 30_000,
) {
    data class FallEvent(val timeMs: Long, val peak: Double, val freeFallSeen: Boolean)

    private var lastFreeFallMs = Long.MIN_VALUE / 2
    private var impactMs = -1L
    private var peak = 0.0
    private var freeFall = false
    private var devSum = 0.0
    private var devMax = 0.0
    private var devCount = 0
    private var lastEventMs = Long.MIN_VALUE / 2

    fun reset() { impactMs = -1; lastFreeFallMs = Long.MIN_VALUE / 2 }

    /** 센서 샘플마다 호출. 낙상이 확정되면 FallEvent 반환. */
    fun feed(timeMs: Long, x: Float, y: Float, z: Float): FallEvent? {
        val mag = sqrt((x * x + y * y + z * z).toDouble())
        if (mag < freeFallThreshold) lastFreeFallMs = timeMs

        if (impactMs < 0) {
            if (mag >= impactThreshold && timeMs - lastEventMs > cooldownMs) {
                impactMs = timeMs
                peak = mag
                freeFall = timeMs - lastFreeFallMs <= 1000
                devSum = 0.0; devMax = 0.0; devCount = 0
            }
            return null
        }

        if (mag > peak) peak = mag
        val since = timeMs - impactMs
        if (since < stillDelayMs) return null

        if (since <= stillDelayMs + stillWindowMs) {
            val dev = abs(mag - 9.81)
            devSum += dev
            if (dev > devMax) devMax = dev
            devCount++
            return null
        }

        // 관찰 구간 종료 -> 판정
        val still = devCount > 0 && devSum / devCount <= stillDeviationMax && devMax <= stillSpikeMax
        val result = if (still) FallEvent(timeMs, peak, freeFall) else null
        if (still) lastEventMs = timeMs
        impactMs = -1
        return result
    }
}