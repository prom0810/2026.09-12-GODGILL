package com.safewalk.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteAStarTest {

    @Test
    fun shortestRouteIsSelectedWhenSafetyWeightIsZero() {
        val result = RouteAStar.findRoute(routeGraph(), "start", "end", safetyWeight = 0.0)

        assertEquals(listOf("start", "short", "end"), result?.nodeIds())
    }

    @Test
    fun saferRouteIsSelectedWhenSafetyWeightIsHigh() {
        val result = RouteAStar.findRoute(routeGraph(), "start", "end", safetyWeight = 1.0)

        assertEquals(listOf("start", "safe", "end"), result?.nodeIds())
        assertEquals(1.0, result?.averageSafetyScore ?: 0.0, 0.0001)
    }

    @Test
    fun missingDestinationReturnsNoRoute() {
        val result = RouteAStar.findRoute(routeGraph(), "start", "missing", safetyWeight = 0.5)

        assertNull(result)
    }

    @Test
    fun disconnectedDestinationReturnsNoRoute() {
        val graph = routeGraph().let { existing ->
            RouteGraph(
                nodes = existing.nodes + ("isolated" to RouteNode("isolated", 0.0, 0.0)),
                adjacency = existing.adjacency,
            )
        }

        assertNull(RouteAStar.findRoute(graph, "start", "isolated", safetyWeight = 0.5))
    }

    private fun routeGraph(): RouteGraph {
        val nodes = listOf("start", "short", "safe", "end")
            .associateWith { RouteNode(it, 0.0, 0.0) }

        return RouteGraph(
            nodes = nodes,
            adjacency = mapOf(
                "start" to listOf(
                    RouteEdge("short", distanceMeters = 100.0, safetyScore = 0.1),
                    RouteEdge("safe", distanceMeters = 150.0, safetyScore = 1.0),
                ),
                "short" to listOf(RouteEdge("end", distanceMeters = 100.0, safetyScore = 0.1)),
                "safe" to listOf(RouteEdge("end", distanceMeters = 150.0, safetyScore = 1.0)),
            ),
        )
    }

    private fun RouteAStar.RouteResult.nodeIds(): List<String> =
        listOf(segments.first().from.id) + segments.map { it.to.id }
}
