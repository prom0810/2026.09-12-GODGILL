package com.safewalk.location

import com.safewalk.map.model.RiskZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 위험구역 접근(경계 50m 전)·진입 알림, 구역당 1번, 100m 넘게 멀어지면 초기화 */
class RiskZoneTrackerTest {
    private val lat0 = 36.8100
    private val lng0 = 127.1460
    private val dLat = 1 / 111195.0
    private val dLng = 1 / (111195.0 * Math.cos(Math.toRadians(lat0)))
    private fun p(eastM: Double, northM: Double) = doubleArrayOf(lat0 + northM * dLat, lng0 + eastM * dLng)

    // 구역 A: 동쪽 500m, 반경 100m (경계는 동쪽 400m)
    private val zoneA = p(500.0, 0.0).let { RiskZone(1, it[0], it[1], 100.0, 0.7, "A") }

    private fun walk(t: RiskZoneTracker, vararg east: Double, north: Double = 0.0): List<String> =
        east.flatMap { e -> p(e, north).let { q -> t.update(q[0], q[1]) } }
            .map { "${it.kind}:${Math.round(it.distanceToEdgeMeters)}" }

    @Test
    fun approachThenEnterOnce() {
        assertEquals(
            listOf("APPROACHING:40", "ENTERED:0"),
            walk(RiskZoneTracker(listOf(zoneA)), 330.0, 360.0, 380.0, 420.0, 500.0, 620.0, 700.0),
        )
    }

    @Test
    fun startingInsideOnlyEnters() {
        assertEquals(listOf("ENTERED:0"), walk(RiskZoneTracker(listOf(zoneA)), 480.0, 490.0))
    }

    @Test
    fun jitterNearEdgeAlertsOnce() {
        assertEquals(listOf("APPROACHING:40"), walk(RiskZoneTracker(listOf(zoneA)), 360.0, 340.0, 360.0, 330.0, 355.0))
    }

    @Test
    fun leavingFarResetsZone() {
        assertEquals(listOf("APPROACHING:40", "APPROACHING:40"), walk(RiskZoneTracker(listOf(zoneA)), 360.0, 250.0, 360.0))
    }

    @Test
    fun passingSixtyMetersAwayIsSilent() {
        assertTrue(walk(RiskZoneTracker(listOf(zoneA)), 300.0, 400.0, 500.0, 600.0, north = 160.0).isEmpty())
    }

    @Test
    fun inheritedStateSuppressesRepeat() {
        val old = RiskZoneTracker(listOf(zoneA)).also { walk(it, 360.0) }
        val updated = RiskZoneTracker(listOf(zoneA)).also { it.inheritFrom(old) }
        assertTrue(walk(updated, 365.0).isEmpty())
    }
}
