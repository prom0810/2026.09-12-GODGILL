package com.safewalk.map

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 도로 구간(edge)의 안전도 점수를 계산한다.
 * MapActivity가 이미 지도 화면 표시를 위해 불러온 CCTV·보안등·안전시설 데이터를
 * 그대로 재사용하기 때문에 이 계산 자체는 추가 네트워크 요청이 필요 없다.
 *
 * S(e) = 0.85 - 위험구역 감점 + CCTV 근접보너스 + 보안등 근접보너스 + 안전시설 근접보너스
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

    private const val CCTV_BOOST_RADIUS_M = 150.0
    private const val CCTV_BOOST_AMOUNT = 0.15
    private const val SECURITY_LIGHT_BOOST_RADIUS_M = 100.0
    private const val SECURITY_LIGHT_BOOST_AMOUNT = 0.10
    private const val FACILITY_BOOST_RADIUS_M = 150.0
    private const val FACILITY_BOOST_AMOUNT = 0.15

    /**
     * 정규화 없이 원시 안전도 점수를 계산한다.
     * 그래프 전체 min~max 기준 정규화는 [RoadNetworkProvider]가 담당한다
     * (이론적 상/하한으로 정규화하면 실제 점수 차이가 눌려버리는 문제가 있어,
     *  실제 관찰된 값 범위를 쓰는 동적 정규화 방식을 쓴다).
     */
    fun rawSafetyScore(
        midLat: Double,
        midLon: Double,
        cctvSites: List<CctvSite>,
        securityLightSites: List<SecurityLightSite>,
        facilities: List<Facility>,
    ): Double {
        var raw = 0.85

        for (zone in dangerZones) {
            val (zoneLat, zoneLon) = offsetToLatLon(zone.latOffsetM, zone.lonOffsetM)
            val dist = haversineMeters(midLat, midLon, zoneLat, zoneLon)
            if (dist < zone.radiusM) {
                raw -= zone.severity * (1.0 - dist / zone.radiusM)
            }
        }

        if (cctvSites.isNotEmpty()) {
            val nearest = cctvSites.minOf { haversineMeters(midLat, midLon, it.latitude, it.longitude) }
            if (nearest < CCTV_BOOST_RADIUS_M) {
                raw += CCTV_BOOST_AMOUNT * (1.0 - nearest / CCTV_BOOST_RADIUS_M)
            }
        }

        if (securityLightSites.isNotEmpty()) {
            val nearest = securityLightSites.minOf { haversineMeters(midLat, midLon, it.latitude, it.longitude) }
            if (nearest < SECURITY_LIGHT_BOOST_RADIUS_M) {
                raw += SECURITY_LIGHT_BOOST_AMOUNT * (1.0 - nearest / SECURITY_LIGHT_BOOST_RADIUS_M)
            }
        }

        if (facilities.isNotEmpty()) {
            val nearest = facilities.minOf { haversineMeters(midLat, midLon, it.latitude, it.longitude) }
            if (nearest < FACILITY_BOOST_RADIUS_M) {
                raw += FACILITY_BOOST_AMOUNT * (1.0 - nearest / FACILITY_BOOST_RADIUS_M)
            }
        }

        return raw
    }

    /** 그래프 전체에서 실제로 관찰된 min~max 범위를 기준으로 raw 점수를 0~1로 정규화한다. */
    fun normalize(raw: Double, min: Double, max: Double): Double {
        if (max - min < 1e-6) return 0.5
        return ((raw - min) / (max - min)).coerceIn(0.0, 1.0)
    }

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