package com.foresto.gatgil.route

import com.foresto.gatgil.model.Edge
import com.foresto.gatgil.model.Node
import java.util.PriorityQueue

/**
 * 거리와 안전도를 함께 고려하는 A* 경로탐색.
 *
 * 간선 비용 = 거리 * (1 + safetyWeight * (1 - safetyScore) * PENALTY_FACTOR)
 *  - safetyWeight가 클수록 안전도가 낮은 구간에 더 큰 페널티를 준다.
 *  - safetyWeight = 0 이면 순수 최단거리 경로와 동일해진다. (최단경로 모드에서 사용)
 *  - safetyWeight > 0 이면 이동자 유형별 안전 가중치를 반영한 경로가 된다. (안전경로 모드에서 사용)
 *
 * 휴리스틱은 목적지까지의 직선거리(haversine)를 사용한다.
 * 실제 비용은 항상 거리 이상이므로(위 식에서 1+... >= 1) 이 휴리스틱은 admissible하여
 * A*가 최적 경로를 보장한다.
 */
object AStarRouter {

    private const val PENALTY_FACTOR = 6.0

    data class RouteSegment(val from: Node, val to: Node, val distanceMeters: Double, val safetyScore: Double)

    data class RouteResult(
        val segments: List<RouteSegment>,
        val totalDistanceMeters: Double,
        val averageSafetyScore: Double
    ) {
        /** 경로가 지나는 노드 목록 (시작점 포함) */
        fun pathNodes(): List<Node> {
            if (segments.isEmpty()) return emptyList()
            return listOf(segments.first().from) + segments.map { it.to }
        }
    }

    fun findRoute(
        graph: SafetyDataProvider.Graph,
        startId: String,
        goalId: String,
        safetyWeight: Double
    ): RouteResult? {
        val nodes = graph.nodes
        val goal = nodes[goalId] ?: return null

        val gScore = HashMap<String, Double>().apply { put(startId, 0.0) }
        val cameFrom = HashMap<String, String>()
        val cameFromEdge = HashMap<String, Edge>()
        val visited = HashSet<String>()

        val open = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        open.add(startId to heuristic(nodes[startId]!!, goal))

        while (open.isNotEmpty()) {
            val (currentId, _) = open.poll()
            if (currentId == goalId) {
                return reconstruct(cameFrom, cameFromEdge, nodes, startId, goalId)
            }
            if (!visited.add(currentId)) continue

            val currentNode = nodes[currentId] ?: continue
            val currentG = gScore[currentId] ?: Double.MAX_VALUE

            for (edge in graph.adjacency[currentId].orEmpty()) {
                if (edge.toNodeId in visited) continue

                val edgeCost = edge.distanceMeters * (1 + safetyWeight * (1 - edge.safetyScore) * PENALTY_FACTOR)
                val tentativeG = currentG + edgeCost

                if (tentativeG < (gScore[edge.toNodeId] ?: Double.MAX_VALUE)) {
                    gScore[edge.toNodeId] = tentativeG
                    cameFrom[edge.toNodeId] = currentId
                    cameFromEdge[edge.toNodeId] = edge
                    val toNode = nodes[edge.toNodeId] ?: continue
                    val f = tentativeG + heuristic(toNode, goal)
                    open.add(edge.toNodeId to f)
                }
            }
        }

        return null // 경로 없음
    }

    private fun heuristic(a: Node, b: Node): Double =
        SafetyDataProvider.haversineMeters(a.lat, a.lon, b.lat, b.lon)

    private fun reconstruct(
        cameFrom: Map<String, String>,
        cameFromEdge: Map<String, Edge>,
        nodes: Map<String, Node>,
        startId: String,
        goalId: String
    ): RouteResult {
        val path = ArrayDeque<String>()
        var cur = goalId
        path.addFirst(cur)
        while (cur != startId) {
            cur = cameFrom[cur] ?: break
            path.addFirst(cur)
        }

        val segments = mutableListOf<RouteSegment>()
        var totalDist = 0.0
        var safetySum = 0.0

        for (id in path.drop(1)) {
            val edge = cameFromEdge[id] ?: continue
            val fromId = cameFrom[id] ?: continue
            val fromNode = nodes[fromId] ?: continue
            val toNode = nodes[id] ?: continue
            segments.add(RouteSegment(fromNode, toNode, edge.distanceMeters, edge.safetyScore))
            totalDist += edge.distanceMeters
            safetySum += edge.safetyScore
        }

        val avgSafety = if (segments.isNotEmpty()) safetySum / segments.size else 1.0
        return RouteResult(segments, totalDist, avgSafety)
    }
}
