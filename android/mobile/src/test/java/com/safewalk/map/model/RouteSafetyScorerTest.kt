package com.safewalk.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSafetyScorerTest {

    @Test
    fun missingFacilityDataIsNeutralRatherThanEvidenceOfSafetyOrDanger() {
        assertEquals(.5, RouteSafetyScorer.score(36.83, 127.18, emptyList(), emptyList(), emptyList()).raw, 1e-9)
    }

    @Test
    fun allDefaultFacilitiesTogetherCanAddAtMostPointOne() {
        val result = RouteSafetyScorer.score(36.83, 127.18,
            listOf(CctvSite(36.83, 127.18, "", "생활방범", 20)),
            listOf(SecurityLightSite(36.83, 127.18, "", "", 20)),
            listOf(Facility("police", FacilityType.SECURITY, "경찰", "", 36.83, 127.18)))
        assertEquals(.60, result.raw, 1e-9)
        assertEquals(.10, result.facilities.sumOf { it.appliedScore }, 1e-9)
    }

    @Test
    fun raisingABonusIsNotCancelledByGraphMinMaxScaling() {
        assertEquals(.51, RouteSafetyScorer.fixedScore(0.0, .01), 1e-9)
        assertEquals(.52, RouteSafetyScorer.fixedScore(0.0, .02), 1e-9)
    }

    @Test
    fun trafficAndUnknownPurposeCamerasDoNotCountAsCrimePreventionCameras() {
        val result = RouteSafetyScorer.score(36.83, 127.18,
            listOf(CctvSite(36.83, 127.18, "", "교통단속", 1), CctvSite(36.83, 127.18, "", "", 1)),
            emptyList(), emptyList())
        assertTrue(result.facilities.isEmpty())
        assertEquals(.5, result.raw, 1e-9)
    }

    @Test
    fun abundantFacilitiesDoNotCancelAKnownHighRiskArea() {
        val lat = RouteSafetyScorer.CENTER_LATITUDE - 600.0 / 111_320.0
        val lon = RouteSafetyScorer.CENTER_LONGITUDE + 300.0 /
            (111_320.0 * kotlin.math.cos(Math.toRadians(RouteSafetyScorer.CENTER_LATITUDE)))
        val result = RouteSafetyScorer.score(lat, lon,
            listOf(CctvSite(lat, lon, "", "방범", 1)),
            listOf(SecurityLightSite(lat, lon, "", "", 1)),
            listOf(Facility("police", FacilityType.SECURITY, "경찰", "", lat, lon)))
        assertTrue(result.riskPenalty >= .69)
        assertEquals(.10 * (1 - result.riskPenalty), result.facilities.sumOf { it.appliedScore }, 1e-9)
        assertEquals(0.0, result.raw, 1e-9)
    }

    @Test
    fun streetLightSupportDecreasesWithDistanceAndIsZeroOutsideSearchRadius() {
        fun score(offset: Double) = RouteSafetyScorer.score(36.83, 127.18, emptyList(),
            listOf(SecurityLightSite(36.83 + offset, 127.18, "", "", 1)), emptyList())
        assertTrue(score(.00045).raw < score(0.0).raw)
        assertEquals(.5, score(.002).raw, 1e-9)
    }

    @Test
    fun policeProximityImprovesScoreAndDecreasesWithDistance() {
        fun score(offset: Double) = RouteSafetyScorer.score(36.83, 127.18, emptyList(), emptyList(),
            listOf(Facility("police", FacilityType.SECURITY, "경찰", "", 36.83 + offset, 127.18)))
        assertEquals(.54, score(0.0).raw, 1e-9)
        assertTrue(score(.00027).raw > .5)
        assertTrue(score(.00027).raw < score(0.0).raw)
        assertEquals(.5, score(.002).raw, 1e-9)
    }
}
