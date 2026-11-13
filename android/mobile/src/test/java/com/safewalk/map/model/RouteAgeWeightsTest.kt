package com.safewalk.map.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RouteAgeWeightsTest {
    private val under14 = RouteSafetyWeights(RouteSafetyWeights.DEFAULT.facilityWeights + mapOf(
        "CCTV" to .20, "STREET_LIGHT" to .20, "POLICE" to .30,
        "EMERGENCY_BELL" to .25, "FIRE_STATION" to .20,
    ))

    @Test
    fun `age-specific settings change the chosen safe route through the road provider and parser`() {
        // 좌우 갈림길: 왼쪽에는 CCTV, 조금 더 짧은 오른쪽에는 가로등이 있다.
        val start = RouteNode("1", 36.8300, 127.1800)
        val left = RouteNode("2", 36.8320, 127.1776)
        val right = RouteNode("3", 36.8320, 127.1822)
        val end = RouteNode("4", 36.8340, 127.1800)
        fun midpoint(a: RouteNode, b: RouteNode) = (a.lat + b.lat) / 2 to (a.lon + b.lon) / 2
        val cctv = listOf(start to left, left to end).map { (a, b) ->
            val (lat, lon) = midpoint(a, b)
            CctvSite(lat, lon, "주소", "방범", 1)
        }
        val lights = listOf(start to right, right to end).map { (a, b) ->
            val (lat, lon) = midpoint(a, b)
            SecurityLightSite(lat, lon, "등", "주소", 1)
        }
        val json = """{"elements":[
            {"type":"node","id":1,"lat":${start.lat},"lon":${start.lon}},
            {"type":"node","id":2,"lat":${left.lat},"lon":${left.lon}},
            {"type":"node","id":3,"lat":${right.lat},"lon":${right.lon}},
            {"type":"node","id":4,"lat":${end.lat},"lon":${end.lon}},
            {"type":"way","id":10,"nodes":[1,2,4]},
            {"type":"way","id":11,"nodes":[1,3,4]}]}"""
        val provider = RoadNetworkProvider(mirrors = emptyList(), bundledDataSource = BundledRoadNetworkDataSource { json })
        fun route(weights: RouteSafetyWeights) = RouteAStar.findRoute(
            provider.buildGraph(cctv, lights, emptyList(), weights), "1", "4", .55,
        )!!

        val standard = route(RouteSafetyWeights.DEFAULT)
        val child = route(under14)
        assertEquals("2", standard.segments.first().to.id)
        assertEquals("3", child.segments.first().to.id)
        assertEquals(.20, child.segments.first().facilities.single().appliedScore, 1e-9)
    }

    @Test
    fun `each facility type contributes its own weighted nearest site`() {
        val lat = 36.83
        val lon = 127.18
        val facilities = listOf(
            Facility("store", FacilityType.CONVENIENCE_STORE, "편의점", "", lat, lon),
            Facility("police", FacilityType.SECURITY, "경찰", "", lat, lon + .0001),
            Facility("police2", FacilityType.SECURITY, "먼 경찰", "", lat, lon + .0002),
        )
        val standard = RouteSafetyScorer.score(lat, lon, emptyList(), emptyList(), facilities)
        val child = RouteSafetyScorer.score(lat, lon, emptyList(), emptyList(), facilities, under14)
        assertEquals(2, child.facilities.size)
        val standardPolice = standard.facilities.single { it.type == "POLICE" }
        val childPolice = child.facilities.single { it.type == "POLICE" }
        assertEquals(standardPolice.appliedScore * 2, childPolice.appliedScore, 1e-9)
        assertEquals("police", facilities.single { it.latitude == childPolice.lat && it.longitude == childPolice.lon }.id)
        assertTrue(child.raw > standard.raw)
        assertEquals(standard.riskPenalty, child.riskPenalty, 0.0)
    }

    @Test
    fun `server weights are decoded and calculation band is saved with guidance`() {
        val profile = RouteSafetyProfile.fromJson(JSONObject()
            .put("birthDateRequired", false).put("ageBand", "UNDER_14")
            .put("facilityWeights", JSONObject(under14.facilityWeights)))
        assertEquals(RouteAgeBand.UNDER_14, profile.ageBand)
        assertEquals(under14, profile.weights)
        val a = RouteNode("a", 36.83, 127.18)
        val b = RouteNode("b", 36.831, 127.18)
        val result = RouteAStar.RouteResult(listOf(RouteAStar.RouteSegment(a, b)), 111.0, .5, profile.ageBand)
        assertEquals("UNDER_14", buildRouteStartRequest(result, "A", "B").getString("safetyAgeBand"))
    }

    @Test(expected = BirthDateRequiredException::class)
    fun `unknown age requests input without defaulting to age14plus`() {
        RouteSafetyProfile.fromJson(JSONObject().put("birthDateRequired", true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid configuration cannot produce nan scores`() {
        RouteSafetyWeights(under14.facilityWeights + ("CCTV" to Double.NaN))
    }
}
