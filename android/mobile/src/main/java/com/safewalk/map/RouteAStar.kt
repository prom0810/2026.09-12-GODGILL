package com.safewalk.map

import java.util.PriorityQueue

/**
 * 안전도 가중 A* 경로탐색.
 * C(e) = d(e) x (1 + w x (1 - S(e)) x k)
 * d(e)=구간 거리, S(e)=구간 안전도(0~1), w=안전 가중치, k=페널티 강도
 *
 * 덧셈/뺄셈식 대신 곱셈 구조를 쓰는 이유: 비용이 항상 양수로 유지되어야
 * A 다익스트라의 정확성이 깨지지 않는다.
 */
object RouteAStar {

    private const val PENALTY_FACTOR = 6.0

    data class RouteSegment(val from: RouteNode, val to: RouteNode)

    data class RouteResult(
        val segments: List<RouteSegment>,
        val totalDistanceMeters: Double,
        val averageSafetyScore: Double,
    )

    fun findRoute(graph: RouteGraph, startId: String, endId: String, safetyWeight: Double): RouteResult? {
        if (startId == endId) return null
        val startNode = graph.nodes[startId] ?: return null
        val endNode = graph.nodes[endId] ?: return null

        data class Entry(val nodeId: String, val g: Double, val f: Double)

        val open = PriorityQueue<Entry>(16, compareBy { it.f })
        val bestG = HashMap<String, Double>()
        val cameFrom = HashMap<String, String>()
        val edgeSafetyIntoNode = HashMap<String, Double>()
        val visited = HashSet<String>()

        bestG[startId] = 0.0
        open += Entry(startId, 0.0, heuristic(startNode, endNode))

        while (open.isNotEmpty()) {
            val current = open.poll()
            if (current.nodeId in visited) continue
            visited += current.nodeId
            if (current.nodeId == endId) break

            for (edge in graph.adjacency[current.nodeId].orEmpty()) {
                val cost = edge.distanceMeters * (1 + safetyWeight * (1 - edge.safetyScore) * PENALTY_FACTOR)
                val tentativeG = current.g + cost
                if (tentativeG < (bestG[edge.toNodeId] ?: Double.MAX_VALUE)) {
                    bestG[edge.toNodeId] = tentativeG
                    cameFrom[edge.toNodeId] = current.nodeId
                    edgeSafetyIntoNode[edge.toNodeId] = edge.safetyScore
                    val toNode = graph.nodes[edge.toNodeId] ?: continue
                    open += Entry(edge.toNodeId, tentativeG, tentativeG + heuristic(toNode, endNode))
                }
            }
        }

        if (endId !in cameFrom) return null

        val pathIds = mutableListOf(endId)
        var cursor = endId
        while (cursor != startId) {
            cursor = cameFrom[cursor] ?: return null
            pathIds += cursor
        }
        pathIds.reverse()

        val segments = mutableListOf<RouteSegment>()
        var totalDistance = 0.0
        var safetySum = 0.0
        for (i in 0 until pathIds.size - 1) {
            val from = graph.nodes[pathIds[i]] ?: continue
            val to = graph.nodes[pathIds[i + 1]] ?: continue
            segments += RouteSegment(from, to)
            totalDistance += RouteSafetyScorer.haversineMeters(from.lat, from.lon, to.lat, to.lon)
            safetySum += edgeSafetyIntoNode[pathIds[i + 1]] ?: 0.5
        }

        return RouteResult(
            segments = segments,
            totalDistanceMeters = totalDistance,
            averageSafetyScore = if (segments.isEmpty()) 0.0 else safetySum / segments.size,
        )
    }

    private fun heuristic(a: RouteNode, b: RouteNode): Double =
        RouteSafetyScorer.haversineMeters(a.lat, a.lon, b.lat, b.lon)
}
