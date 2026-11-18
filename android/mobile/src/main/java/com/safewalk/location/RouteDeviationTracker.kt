package com.safewalk.location

/**
 * 경로 이탈 판단 (Android 의존 없음 → 단위 테스트 가능).
 *
 * 위치가 올 때마다 [update]를 부른다. 안내 경로(지점 목록, 구간 = 지점 i → i+1)까지의 최단 거리로 판단한다.
 *  - 경로에서 [thresholdMeters] 넘게 떨어진 위치가 [requiredCount]번 연속 → [Event.DEVIATED] (이탈 1회당 1번)
 *  - 이탈 중 [returnMeters] 이내로 돌아오면 → [Event.RETURNED] (경계에서 GPS가 흔들려 알림이 반복되지 않게 기준을 낮게 둔다)
 *  - 출발 직후 아직 경로 위에 한 번도 올라오지 않았으면 판단하지 않는다 (출발지가 도로망 노드에서 떨어진 경우 오판 방지)
 *  - 도착지 [arrivalMeters] 이내면 판단하지 않는다 (도착지 건물 안·주변을 이탈로 보지 않음)
 *
 * 거리는 구간마다 시작점 기준 평면 근사(서버 SegmentMatcher와 같은 방식). 천안역 주변 1~2km 범위라 충분하다.
 */
class RouteDeviationTracker(
    path: List<DoubleArray>,
    private val thresholdMeters: Double = 30.0,
    private val requiredCount: Int = 2,
    private val returnMeters: Double = 20.0,
    private val arrivalMeters: Double = 30.0,
) {
    enum class Event { NONE, DEVIATED, RETURNED }

    private val path: List<DoubleArray> = path.toList()
    private var armed = false
    private var offCount = 0

    /** 지금 이탈 상태인지 */
    var deviated = false
        private set

    /** 마지막으로 계산한 경로까지 거리(m). 알림 문구에 쓴다 */
    var lastDistanceMeters = 0.0
        private set

    init {
        require(this.path.size >= 2) { "경로 지점이 2개 이상이어야 합니다." }
    }

    fun update(lat: Double, lng: Double): Event {
        val end = path.last()
        if (distanceBetween(lat, lng, end[0], end[1]) <= arrivalMeters) {
            offCount = 0
            return leaveDeviation()
        }
        val d = distanceToPath(lat, lng)
        lastDistanceMeters = d
        if (d <= thresholdMeters) {
            armed = true
            offCount = 0
            return if (d <= returnMeters) leaveDeviation() else Event.NONE
        }
        if (!armed || deviated) return Event.NONE
        offCount++
        if (offCount >= requiredCount) {
            deviated = true
            return Event.DEVIATED
        }
        return Event.NONE
    }

    private fun leaveDeviation(): Event {
        if (!deviated) return Event.NONE
        deviated = false
        return Event.RETURNED
    }

    fun distanceToPath(lat: Double, lng: Double): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until path.size - 1) {
            val d = distanceToSegment(path[i], path[i + 1], lat, lng)
            if (d < best) best = d
        }
        return best
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun distanceToSegment(a: DoubleArray, b: DoubleArray, lat: Double, lng: Double): Double {
            val cos = Math.cos(Math.toRadians(a[0]))
            val bx = Math.toRadians(b[1] - a[1]) * cos * EARTH_RADIUS_M
            val by = Math.toRadians(b[0] - a[0]) * EARTH_RADIUS_M
            val px = Math.toRadians(lng - a[1]) * cos * EARTH_RADIUS_M
            val py = Math.toRadians(lat - a[0]) * EARTH_RADIUS_M
            val len2 = bx * bx + by * by
            val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            val dx = px - t * bx
            val dy = py - t * by
            return Math.sqrt(dx * dx + dy * dy)
        }

        fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
            distanceToSegment(doubleArrayOf(lat1, lng1), doubleArrayOf(lat1, lng1), lat2, lng2)
    }
}
