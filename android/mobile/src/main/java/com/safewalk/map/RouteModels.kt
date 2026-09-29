package com.safewalk.map

/** 도로망 그래프의 정점(교차점/노드) */
data class RouteNode(val id: String, val lat: Double, val lon: Double)

/** 도로망 그래프의 간선(구간). 거리와 이미 정규화된 안전도 점수(0~1)를 갖는다. */
data class RouteEdge(val toNodeId: String, val distanceMeters: Double, val safetyScore: Double)

/** OSM 실도로망 또는 격자 목업으로 만들어진 경로탐색용 그래프 */
class RouteGraph(
    val nodes: Map<String, RouteNode>,
    val adjacency: Map<String, List<RouteEdge>>,
) {
    /** 주어진 좌표에서 가장 가까운 그래프 노드를 찾는다 (출발지/도착지를 노드에 스냅할 때 사용). */
    fun nearestNode(lat: Double, lon: Double): RouteNode? =
        nodes.values.minByOrNull { RouteSafetyScorer.haversineMeters(it.lat, it.lon, lat, lon) }
}
