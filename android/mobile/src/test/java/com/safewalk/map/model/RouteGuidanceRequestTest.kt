package com.safewalk.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGuidanceRequestTest {

    private val start = RouteNode("a", 36.8100, 127.1467)
    private val mid = RouteNode("b", 36.8105, 127.1467)
    private val end = RouteNode("c", 36.8110, 127.1467)
    private val cctv = AppliedFacility("CCTV", 36.8102, 127.1468, 2, 35.2, 0.115)

    private fun result() = RouteAStar.RouteResult(
        segments = listOf(
            RouteAStar.RouteSegment(start, mid, safetyScore = 0.8, riskPenalty = 0.0, facilities = listOf(cctv)),
            RouteAStar.RouteSegment(mid, end, safetyScore = 0.3, riskPenalty = 0.42),
        ),
        totalDistanceMeters = 134.0,
        averageSafetyScore = 0.55,
    )

    @Test
    fun `points are segment nodes in order with names only at both ends`() {
        val body = buildRouteStartRequest(result(), "천안역", "천안시청")
        val points = body.getJSONArray("points")

        assertEquals(3, points.length())
        assertEquals(36.8100, points.getJSONObject(0).getDouble("lat"), 1e-9)
        assertEquals("천안역", points.getJSONObject(0).getString("name"))
        assertFalse(points.getJSONObject(1).has("name"))
        assertEquals("천안시청", points.getJSONObject(2).getString("name"))
    }

    @Test
    fun `segments carry safety score risk penalty and applied facilities`() {
        val body = buildRouteStartRequest(result(), "A", "B")
        val segments = body.getJSONArray("segments")

        assertEquals(2, segments.length())
        val first = segments.getJSONObject(0)
        assertEquals(0.8, first.getDouble("safetyScore"), 1e-9)
        assertEquals(0.0, first.getDouble("riskScore"), 1e-9)
        val facility = first.getJSONArray("facilities").getJSONObject(0)
        assertEquals("CCTV", facility.getString("type"))
        assertEquals(2, facility.getInt("quantity"))
        assertEquals(0.115, facility.getDouble("appliedScore"), 1e-9)
        assertEquals(0.42, segments.getJSONObject(1).getDouble("riskScore"), 1e-9)
        assertEquals(0, segments.getJSONObject(1).getJSONArray("facilities").length())
    }

    @Test
    fun `estimated time is walking seconds at 67 meters per minute`() {
        val body = buildRouteStartRequest(result(), "A", "B")

        assertEquals(120, body.getInt("estimatedTime")) // 134m / 67 × 60
        assertEquals(0.55, body.getDouble("safetyScore"), 1e-9)
    }

    @Test
    fun `scorer breakdown matches raw score and reports nearby cctv`() {
        val site = CctvSite(36.8100, 127.1468, "주소", "방범", 3)
        val breakdown = RouteSafetyScorer.score(36.8100, 127.1467, listOf(site), emptyList(), emptyList())
        val raw = RouteSafetyScorer.rawSafetyScore(36.8100, 127.1467, listOf(site), emptyList(), emptyList())

        assertEquals(raw, breakdown.raw, 1e-12)
        assertEquals(1, breakdown.facilities.size)
        assertEquals("CCTV", breakdown.facilities[0].type)
        assertEquals(3, breakdown.facilities[0].quantity)
        assertTrue(breakdown.facilities[0].appliedScore > 0.0)
        assertEquals(0.85 - breakdown.riskPenalty + breakdown.facilities.sumOf { it.appliedScore }, breakdown.raw, 1e-12)
    }

    @Test
    fun `a star keeps score breakdown of the edges it used`() {
        val graph = RouteGraph(
            nodes = listOf(start, end).associateBy { it.id },
            adjacency = mapOf(
                "a" to listOf(RouteEdge("c", 100.0, 0.6, riskPenalty = 0.2, facilities = listOf(cctv))),
            ),
        )
        val route = RouteAStar.findRoute(graph, "a", "c", safetyWeight = 0.5)!!

        assertEquals(1, route.segments.size)
        assertEquals(0.6, route.segments[0].safetyScore, 1e-9)
        assertEquals(0.2, route.segments[0].riskPenalty, 1e-9)
        assertEquals(listOf(cctv), route.segments[0].facilities)
    }
}
