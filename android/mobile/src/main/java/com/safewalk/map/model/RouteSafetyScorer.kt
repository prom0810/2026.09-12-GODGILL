package com.safewalk.map.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 도로 구간(edge)의 안전도 점수를 계산한다.
 * MapActivity가 이미 지도 화면 표시를 위해 불러온 CCTV·보안등·안전시설 데이터를
 * 그대로 재사용하기 때문에 이 계산 자체는 추가 네트워크 요청이 필요 없다.
 *
 * 시설 존재는 제한적인 환경 지표다. 실제 조도·촬영 범위·범죄 확률로 해석하지 않는다.
 * 고정 기준 점수 = clamp(0.5 - 위험감점 + 제한된 근접 보너스, 0, 1).
 *
 * 점수 내역([ScoreBreakdown]: 위험구역 감점 합, 보너스를 준 시설)은 안내 시작 시 서버에 함께 저장된다
 * (route_segment.risk_score, segment_facility).
 * (브이월드 개방감·조도 항은 이 프로젝트에 해당 데이터가 없어 제외 — 향후 과제)
 */
object RouteSafetyScorer {

    // CheonanCctvRepository / SecurityLightRepository와 동일한 기준 좌표(천안역)
    const val CENTER_LATITUDE = 36.8100
    const val CENTER_LONGITUDE = 127.1467

    private data class DangerZone(
        val latOffsetM: Double,
        val lonOffsetM: Double,
        val radiusM: Double,
        val severity: Double,
    )

    // 실제 범죄주의구간 공공데이터 연동 전까지 쓰는 목업 위험구역 (향후 과제: 실제 데이터로 교체)
    private val dangerZones = listOf(
        DangerZone(-600.0, 300.0, 250.0, 0.7),
        DangerZone(700.0, -400.0, 200.0, 0.55),
        DangerZone(200.0, 900.0, 180.0, 0.6),
        DangerZone(-1100.0, -700.0, 220.0, 0.5),
    )

    // 검색 거리 감쇠용 휴리스틱이며 실제 촬영·조명 도달 범위가 아니다.
    private const val CCTV_BOOST_RADIUS_M = 150.0
    private const val SECURITY_LIGHT_BOOST_RADIUS_M = 100.0
    private const val FACILITY_BOOST_RADIUS_M = 150.0
    private const val NEUTRAL_SCORE = 0.5

    /**
     * 구간 점수 계산 결과와 그 내역.
     *
     * @param raw 고정 기준 점수. 값이 같으면 다른 도로가 추가되어도 점수가 같다.
     * @param riskPenalty 위험구역 감점의 합 (서버 route_segment.risk_score로 저장)
     * @param facilities 보너스를 준 시설 (서버 segment_facility로 저장)
     */
    data class ScoreBreakdown(
        val raw: Double,
        val riskPenalty: Double,
        val facilities: List<AppliedFacility>,
    )

    /**
     * 도로망의 최솟값·최댓값에 의존하지 않는 고정 기준 점수.
     */
    fun rawSafetyScore(
        midLat: Double,
        midLon: Double,
        cctvSites: List<CctvSite>,
        securityLightSites: List<SecurityLightSite>,
        facilities: List<Facility>,
        weights: RouteSafetyWeights = RouteSafetyWeights.DEFAULT,
    ): Double = score(midLat, midLon, cctvSites, securityLightSites, facilities, weights).raw

    /** [rawSafetyScore]와 같은 계산을 하면서, 감점·시설별 보너스 내역도 함께 돌려준다. */
    fun score(
        midLat: Double,
        midLon: Double,
        cctvSites: List<CctvSite>,
        securityLightSites: List<SecurityLightSite>,
        facilities: List<Facility>,
        weights: RouteSafetyWeights = RouteSafetyWeights.DEFAULT,
    ): ScoreBreakdown {
        var riskPenalty = 0.0
        for (zone in dangerZones) {
            val (zoneLat, zoneLon) = offsetToLatLon(zone.latOffsetM, zone.lonOffsetM)
            val dist = haversineMeters(midLat, midLon, zoneLat, zoneLon)
            if (dist < zone.radiusM) {
                riskPenalty += zone.severity * (1.0 - dist / zone.radiusM)
            }
        }

        val applied = mutableListOf<AppliedFacility>()

        val risk = riskPenalty.coerceIn(0.0, 1.0)
        // 알려진 위험이 클수록 시설 근접성으로 상쇄할 수 있는 양도 줄인다.
        val supportFactor = 1.0 - risk
        cctvSites.asSequence().filter { it.purpose.contains("방범") }
            .minByOrNull { haversineMeters(midLat, midLon, it.latitude, it.longitude) }?.let { site ->
            val nearest = haversineMeters(midLat, midLon, site.latitude, site.longitude)
            if (nearest < CCTV_BOOST_RADIUS_M && weights.bonus("CCTV") > 0.0 && supportFactor > 0.0) {
                applied += AppliedFacility(
                    type = "CCTV",
                    lat = site.latitude,
                    lon = site.longitude,
                    quantity = site.cameraCount.coerceAtLeast(1),
                    distanceMeters = nearest,
                    appliedScore = weights.bonus("CCTV") * (1.0 - nearest / CCTV_BOOST_RADIUS_M) * supportFactor,
                )
            }
        }

        securityLightSites.minByOrNull { haversineMeters(midLat, midLon, it.latitude, it.longitude) }?.let { site ->
            val nearest = haversineMeters(midLat, midLon, site.latitude, site.longitude)
            if (nearest < SECURITY_LIGHT_BOOST_RADIUS_M && weights.bonus("STREET_LIGHT") > 0.0 && supportFactor > 0.0) {
                applied += AppliedFacility(
                    type = "STREET_LIGHT",
                    lat = site.latitude,
                    lon = site.longitude,
                    quantity = site.lightCount.coerceAtLeast(1),
                    distanceMeters = nearest,
                    appliedScore = weights.bonus("STREET_LIGHT") * (1.0 - nearest / SECURITY_LIGHT_BOOST_RADIUS_M) * supportFactor,
                )
            }
        }

        // 시설 종류마다 가장 가까운 1곳을 평가한다. 가까운 편의점 때문에 경찰시설이 누락되지 않게 한다.
        for (sameType in facilities.groupBy { it.type }.values) {
            val type = sameType.first().type.serverCode()
            if (weights.bonus(type) == 0.0 || supportFactor == 0.0) continue
            val facility = sameType.minByOrNull { haversineMeters(midLat, midLon, it.latitude, it.longitude) }
                ?: continue
            val nearest = haversineMeters(midLat, midLon, facility.latitude, facility.longitude)
            if (nearest < FACILITY_BOOST_RADIUS_M) {
                applied += AppliedFacility(
                    type = facility.type.serverCode(),
                    lat = facility.latitude,
                    lon = facility.longitude,
                    quantity = 1,
                    distanceMeters = nearest,
                    appliedScore = weights.bonus(type) * (1.0 - nearest / FACILITY_BOOST_RADIUS_M) * supportFactor,
                )
            }
        }

        val raw = fixedScore(riskPenalty, applied.sumOf { it.appliedScore })
        return ScoreBreakdown(raw = raw, riskPenalty = riskPenalty, facilities = applied)
    }

    /** 앱 시설 종류 → 서버 segment_facility.facility_type 코드 */
    private fun FacilityType.serverCode(): String = when (this) {
        FacilityType.SECURITY -> "POLICE"
        FacilityType.CONVENIENCE_STORE -> "CONVENIENCE_STORE"
        FacilityType.FIRE -> "FIRE_STATION"
    }

    /** appliedSupport는 위험도에 따라 감쇠한 보너스다. 시설은 위험감점 자체를 바꾸지 않는다. */
    internal fun fixedScore(riskPenalty: Double, appliedSupport: Double): Double =
        (NEUTRAL_SCORE - riskPenalty.coerceIn(0.0, 1.0) +
            appliedSupport.coerceIn(0.0, RouteSafetyWeights.MAX_TOTAL_BONUS)).coerceIn(0.0, 1.0)

    private fun offsetToLatLon(dNorthM: Double, dEastM: Double): Pair<Double, Double> {
        val dLat = dNorthM / 111_320.0
        val dLon = dEastM / (111_320.0 * cos(Math.toRadians(CENTER_LATITUDE)))
        return (CENTER_LATITUDE + dLat) to (CENTER_LONGITUDE + dLon)
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }
}
