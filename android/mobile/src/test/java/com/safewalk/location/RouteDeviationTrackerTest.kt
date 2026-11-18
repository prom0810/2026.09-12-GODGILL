package com.safewalk.location

import com.safewalk.location.RouteDeviationTracker.Event.DEVIATED
import com.safewalk.location.RouteDeviationTracker.Event.NONE
import com.safewalk.location.RouteDeviationTracker.Event.RETURNED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 경로 이탈 판단 (30m 초과 2회 연속, 20m 이내 복귀, 출발 전·도착 30m 이내 제외) */
class RouteDeviationTrackerTest {
    // 천안역 부근 기준점에서 동쪽·북쪽 m 단위 좌표
    private val lat0 = 36.8100
    private val lng0 = 127.1460
    private val dLat = 1 / 111195.0
    private val dLng = 1 / (111195.0 * Math.cos(Math.toRadians(lat0)))
    private fun p(eastM: Double, northM: Double) = doubleArrayOf(lat0 + northM * dLat, lng0 + eastM * dLng)

    // L자 경로: 동쪽 300m → 북쪽 300m
    private val path = listOf(p(0.0, 0.0), p(300.0, 0.0), p(300.0, 300.0))

    private fun run(vararg pts: Pair<Double, Double>): List<RouteDeviationTracker.Event> {
        val t = RouteDeviationTracker(path)
        return pts.map { (e, n) -> p(e, n).let { t.update(it[0], it[1]) } }
    }

    @Test
    fun distanceToPath() {
        val t = RouteDeviationTracker(path)
        val a = p(150.0, 25.0)
        assertEquals(25.0, t.distanceToPath(a[0], a[1]), 0.5)
        val corner = p(330.0, -40.0)
        assertEquals(50.0, t.distanceToPath(corner[0], corner[1]), 0.5)
    }

    @Test
    fun gpsJitterWithinThresholdIsIgnored() {
        assertTrue(run(0.0 to 0.0, 50.0 to 25.0, 100.0 to -25.0, 150.0 to 0.0).all { it == NONE })
    }

    @Test
    fun singleOutlierIsIgnored() {
        assertTrue(run(0.0 to 0.0, 50.0 to 60.0, 100.0 to 0.0, 150.0 to 60.0, 200.0 to 0.0).all { it == NONE })
    }

    @Test
    fun twoConsecutiveOffRouteFixesAlertOnce() {
        assertEquals(
            listOf(NONE, NONE, NONE, DEVIATED, NONE, NONE),
            run(0.0 to 0.0, 50.0 to 0.0, 60.0 to 35.0, 70.0 to 45.0, 80.0 to 80.0, 90.0 to 120.0),
        )
    }

    @Test
    fun returnNeedsTwentyMetersThenCanAlertAgain() {
        assertEquals(
            listOf(NONE, NONE, DEVIATED, NONE, RETURNED, NONE, DEVIATED),
            run(0.0 to 0.0, 50.0 to 40.0, 60.0 to 40.0, 70.0 to 25.0, 80.0 to 10.0, 90.0 to 40.0, 100.0 to 40.0),
        )
    }

    @Test
    fun notArmedBeforeReachingRoute() {
        assertTrue(run(-50.0 to -40.0, -40.0 to -40.0, -30.0 to -40.0).all { it == NONE })
    }

    @Test
    fun nearDestinationClearsDeviation() {
        assertEquals(
            listOf(NONE, NONE, DEVIATED, RETURNED),
            run(300.0 to 200.0, 340.0 to 200.0, 345.0 to 210.0, 320.0 to 290.0),
        )
    }
}
