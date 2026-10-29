package com.safewalk.map.model

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

internal class OverpassGraphParser {
    @Throws(IOException::class)
    fun parse(json: String, safetyData: RouteSafetyData): RouteGraph = try {
        parseResponse(json, safetyData)
    } catch (error: JSONException) {
        throw IOException("Invalid Overpass response", error)
    }

    private fun parseResponse(json: String, safetyData: RouteSafetyData): RouteGraph {
        val elements = JSONObject(json).getJSONArray("elements")
        val nodes = LinkedHashMap<String, RouteNode>()
        val ways = mutableListOf<List<String>>()

        for (index in 0 until elements.length()) {
            val element = elements.getJSONObject(index)
            when (element.getString("type")) {
                "node" -> {
                    val id = element.getLong("id").toString()
                    nodes[id] = RouteNode(
                        id = id,
                        lat = element.getDouble("lat"),
                        lon = element.getDouble("lon"),
                    )
                }

                "way" -> {
                    val wayNodes = element.getJSONArray("nodes")
                    ways += List(wayNodes.length()) { wayNodes.getLong(it).toString() }
                }
            }
        }

        data class RawEdge(
            val fromId: String,
            val toId: String,
            val distance: Double,
            val rawSafetyScore: Double,
        )

        val rawEdges = mutableListOf<RawEdge>()

        fun collectEdge(fromId: String, toId: String) {
            val from = nodes[fromId] ?: return
            val to = nodes[toId] ?: return
            val distance = RouteSafetyScorer.haversineMeters(from.lat, from.lon, to.lat, to.lon)
            if (distance <= 0.5) return

            val rawSafetyScore = RouteSafetyScorer.rawSafetyScore(
                midLat = (from.lat + to.lat) / 2,
                midLon = (from.lon + to.lon) / 2,
                cctvSites = safetyData.cctvSites,
                securityLightSites = safetyData.securityLightSites,
                facilities = safetyData.facilities,
            )
            rawEdges += RawEdge(fromId, toId, distance, rawSafetyScore)
        }

        for (way in ways) {
            for (index in 0 until way.size - 1) {
                collectEdge(way[index], way[index + 1])
                collectEdge(way[index + 1], way[index])
            }
        }

        val minRaw = rawEdges.minOfOrNull { it.rawSafetyScore } ?: 0.0
        val maxRaw = rawEdges.maxOfOrNull { it.rawSafetyScore } ?: 1.0
        val adjacency = HashMap<String, MutableList<RouteEdge>>()

        for (edge in rawEdges) {
            val normalizedSafety = RouteSafetyScorer.normalize(
                raw = edge.rawSafetyScore,
                min = minRaw,
                max = maxRaw,
            )
            adjacency.getOrPut(edge.fromId) { mutableListOf() } += RouteEdge(
                toNodeId = edge.toId,
                distanceMeters = edge.distance,
                safetyScore = normalizedSafety,
            )
        }

        val connectedIds = adjacency.keys + adjacency.values.flatten().map { it.toNodeId }
        return RouteGraph(nodes.filterKeys { it in connectedIds }, adjacency)
    }
}
