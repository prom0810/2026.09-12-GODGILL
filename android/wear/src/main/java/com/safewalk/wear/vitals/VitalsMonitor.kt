package com.safewalk.wear.vitals

/**
 * 심박수 이상 감지. Android 의존성 없음.
 * - HIGH: highBpm 이상이 highHoldMs 동안 지속
 * - LOW : lowBpm 이하가 lowHoldMs 동안 지속
 * - SPIKE: spikeWindowMs 안에 spikeDelta 이상 급변
 * 의료 진단용이 아니며 임계값은 사용자(나이/운동 여부)에 맞게 조정 필요.
 */
class VitalsMonitor(
    private val highBpm: Double = 150.0,
    private val highHoldMs: Long = 30_000,
    private val lowBpm: Double = 40.0,
    private val lowHoldMs: Long = 20_000,
    private val spikeDelta: Double = 60.0,
    private val spikeWindowMs: Long = 10_000,
    private val cooldownMs: Long = 60_000,
) {
    enum class Kind { HIGH, LOW, SPIKE }
    data class VitalsEvent(val kind: Kind, val bpm: Double, val timeMs: Long)

    private data class Sample(val t: Long, val bpm: Double)
    private val samples = ArrayDeque<Sample>()
    private var lastEventMs = Long.MIN_VALUE / 2

    fun reset() { samples.clear() }

    /** 측정값이 들어올 때마다 호출(bpm<=0 은 무시). */
    fun feed(timeMs: Long, bpm: Double): VitalsEvent? {
        if (bpm <= 0.0) return null
        samples.addLast(Sample(timeMs, bpm))
        val keep = maxOf(highHoldMs, lowHoldMs, spikeWindowMs) + 5_000
        while (samples.isNotEmpty() && timeMs - samples.first().t > keep) samples.removeFirst()
        if (timeMs - lastEventMs < cooldownMs) return null

        val ev = check(timeMs, bpm)
        if (ev != null) lastEventMs = timeMs
        return ev
    }

    private fun check(now: Long, bpm: Double): VitalsEvent? {
        sustained(now, highHoldMs) { it >= highBpm }?.let { return VitalsEvent(Kind.HIGH, bpm, now) }
        sustained(now, lowHoldMs) { it <= lowBpm }?.let { return VitalsEvent(Kind.LOW, bpm, now) }
        val recent = samples.filter { now - it.t <= spikeWindowMs }
        if (recent.size >= 2) {
            val delta = recent.maxOf { it.bpm } - recent.minOf { it.bpm }
            if (delta >= spikeDelta) return VitalsEvent(Kind.SPIKE, bpm, now)
        }
        return null
    }

    private inline fun sustained(now: Long, holdMs: Long, cond: (Double) -> Boolean): Boolean? {
        val win = samples.filter { now - it.t <= holdMs }
        if (win.size < 2) return null
        if (win.last().t - win.first().t < holdMs * 0.9) return null
        return if (win.all { cond(it.bpm) }) true else null
    }
}
