package com.safewalk.location

import com.safewalk.map.model.RiskZone

/**
 * 위험구역 접근·진입 판단 (Android 의존 없음 → 단위 테스트 가능).
 *
 * 위치가 올 때마다 [update]를 부르면 이번에 알릴 일을 돌려준다. 구역마다 따로 판단한다.
 *  - 구역 경계 [approachMeters] 이내로 다가오면 [Kind.APPROACHING] (구역 밖일 때만)
 *  - 구역 반경 안으로 들어오면 [Kind.ENTERED] (접근 알림 없이 바로 안에서 시작해도 진입은 알린다)
 *  - 각각 구역당 1번. 경계에서 [resetMeters] 넘게 멀어지면 초기화되어 다시 오면 또 알린다
 *    (경계 근처에서 GPS가 흔들려도 알림이 반복되지 않게)
 */
class RiskZoneTracker(
    zones: List<RiskZone>,
    private val approachMeters: Double = 50.0,
    private val resetMeters: Double = 100.0,
) {
    enum class Kind { APPROACHING, ENTERED }

    data class Alert(val kind: Kind, val zone: RiskZone, val distanceToEdgeMeters: Double)

    private val zones: List<RiskZone> = zones.toList()
    private val approached = HashSet<Long>()
    private val entered = HashSet<Long>()

    /** 구역 목록이 갱신됐을 때 이미 알린 구역(같은 ID)을 다시 알리지 않도록 이전 상태를 이어받는다 */
    fun inheritFrom(old: RiskZoneTracker) {
        approached += old.approached
        entered += old.entered
    }

    fun update(lat: Double, lng: Double): List<Alert> {
        val alerts = mutableListOf<Alert>()
        for (zone in zones) {
            val toEdge = RouteDeviationTracker.distanceBetween(lat, lng, zone.lat, zone.lng) - zone.radiusM
            when {
                toEdge <= 0.0 -> {
                    approached += zone.id   // 안에 있으면 접근 알림은 의미 없음
                    if (entered.add(zone.id)) alerts += Alert(Kind.ENTERED, zone, 0.0)
                }
                toEdge <= approachMeters -> {
                    if (zone.id !in entered && approached.add(zone.id)) alerts += Alert(Kind.APPROACHING, zone, toEdge)
                }
                toEdge > resetMeters -> {
                    approached -= zone.id
                    entered -= zone.id
                }
            }
        }
        return alerts
    }
}
