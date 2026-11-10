package com.safewalk.map.model

/** 도로망 그래프의 정점(교차점/노드) */
data class RouteNode(val id: String, val lat: Double, val lon: Double)

/**
 * 구간 점수 계산에 실제로 반영된 안전시설 1개 (서버 segment_facility로 저장된다).
 *
 * @param type 서버 시설 코드: CCTV, STREET_LIGHT, POLICE, CONVENIENCE_STORE, FIRE_STATION
 * @param distanceMeters 구간 중간 지점에서 시설까지 거리
 * @param appliedScore 이 시설이 준 보너스 (예: CCTV 0.15 × (1 − 거리/150))
 */
data class AppliedFacility(
    val type: String,
    val lat: Double,
    val lon: Double,
    val quantity: Int,
    val distanceMeters: Double,
    val appliedScore: Double,
)

/**
 * 도로망 그래프의 간선(구간).
 *
 * @param safetyScore 정규화된 안전도(0~1). A*가 비용 계산에 쓰는 값
 * @param riskPenalty 위험구역 감점의 합(0 이상, 정규화 전 절대값). 위험구역 밖이면 0
 * @param facilities 점수에 반영된 안전시설 (CCTV·보안등·주변시설 중 가장 가까운 각 1개, 반경 안일 때만)
 */
data class RouteEdge(
    val toNodeId: String,
    val distanceMeters: Double,
    val safetyScore: Double,
    val riskPenalty: Double = 0.0,
    val facilities: List<AppliedFacility> = emptyList(),
)

/** OSM 실도로망 또는 격자 목업으로 만들어진 경로탐색용 그래프 */
class RouteGraph(
    val nodes: Map<String, RouteNode>,
    val adjacency: Map<String, List<RouteEdge>>,
) {
    /** 주어진 좌표에서 가장 가까운 그래프 노드를 찾는다 (출발지/도착지를 노드에 스냅할 때 사용). */
    fun nearestNode(lat: Double, lon: Double): RouteNode? =
        nodes.values.minByOrNull { RouteSafetyScorer.haversineMeters(it.lat, it.lon, lat, lon) }
}
