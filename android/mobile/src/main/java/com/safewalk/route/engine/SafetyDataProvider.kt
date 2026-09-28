package com.safewalk.route.engine

import com.safewalk.route.model.Edge
import com.safewalk.route.model.Node
import java.util.Calendar
import kotlin.math.*

/**
 * MVP 대상 지역(천안역 반경 1.5km)에 대한 격자형 보행 그래프와
 * 안전도(safetyScore) 목업 데이터를 생성한다.
 *
 * 실제 서비스 단계에서는 이 클래스를 아래 데이터로 교체/결합할 예정:
 *  - 경찰청 범죄주의구간 API
 *  - 고령보행자 사고다발지역 데이터
 *  - 교통약자 이동경로 데이터
 *  - 도로위험도지수
 *  - 사용자 실시간 제보(크라우드소싱)
 *
 * 추가로 아래 두 항을 안전도 점수에 반영한다:
 *  - 개방감 지수(O) — 브이월드 건물 데이터 기반, [VWorldBuildingProvider]
 *  - 시간대별 조도(N/주간 근사) — 야간은 보안등 밀도([StreetlightProvider]), 주간은 개방감으로 근사
 *
 * 지금은 지도교수 권고에 따라 "안전 보장"이 아닌 "안전도 지표 기반 추천"을
 * 데모할 수 있도록, 재현 가능한 의사난수(pseudo-random)로 위험구역 패턴을 만든다.
 */
object SafetyDataProvider {

    // 천안역 실제 좌표 (Google 장소 데이터 기준으로 확인·수정함)
    const val CENTER_LAT = 36.809084
    const val CENTER_LON = 127.146561

    private const val RADIUS_M = 1500.0
    private const val GRID_STEP_M = 150.0 // 격자 간격(약 150m)

    // 데모용 위험구역 중심점들 (범죄주의구간/사고다발지역 목업)
    private data class DangerZone(val latOffsetM: Double, val lonOffsetM: Double, val radiusM: Double, val severity: Double)
    private val dangerZones = listOf(
        DangerZone(-600.0, 300.0, 250.0, 0.7),   // 역 남서측 유흥가 인근 목업
        DangerZone(700.0, -400.0, 200.0, 0.55),  // 골목 밀집지역 목업
        DangerZone(200.0, 900.0, 180.0, 0.6),
        DangerZone(-1100.0, -700.0, 220.0, 0.5)
    )

    // 실제 편의점/지구대·파출소 좌표 (앱 시작 시 MainActivity가 카카오 API로 채워 넣음).
    // 목업 위험구역이 없는 구간에서도 안전경로가 최단경로와 차별화되도록,
    // 이 시설들과 가까울수록 안전도 점수에 가산점을 준다.
    private var facilityPoints: List<Pair<Double, Double>> = emptyList()

    private const val FACILITY_BOOST_RADIUS_M = 150.0
    private const val FACILITY_BOOST_AMOUNT = 0.15

    // 개수 기반 항 (김찬진·신동화, 2025 한국컴퓨터종합학술대회 참고) — 거리 기반 항과 별개로,
    // 반경 내 안전시설이 "몇 개" 몰려있는지도 함께 반영한다. 편의점 1곳만 있는 구간과
    // 편의점+지구대+CCTV가 몰려있는 구간을 구분하기 위함.
    private const val FACILITY_COUNT_RADIUS_M = 150.0
    private const val FACILITY_COUNT_WEIGHT = 0.10
    private const val FACILITY_COUNT_REF = 3 // 이 개수 이상이면 만점

    fun setFacilityPoints(points: List<Pair<Double, Double>>) {
        facilityPoints = points
    }

    // 브이월드 건물 데이터 (개방감 지수 계산용). 앱 시작 시 MainActivity가 채워 넣는다.
    private var buildings: List<VWorldBuildingProvider.BuildingPoint> = emptyList()
    private const val OPENNESS_WEIGHT = 0.15

    fun setBuildings(points: List<VWorldBuildingProvider.BuildingPoint>) {
        buildings = points
    }

    // 야간 보안등 좌표 (야간 조도 항 계산용). 앱 시작 시 MainActivity가 채워 넣는다.
    private var streetlights: List<Pair<Double, Double>> = emptyList()
    private const val NIGHT_LIGHT_WEIGHT = 0.15
    private const val DAY_LIGHT_WEIGHT = 0.10 // 주간은 브이월드 개방감으로 근사 (실측 일조 데이터 아님)

    fun setStreetlights(points: List<Pair<Double, Double>>) {
        streetlights = points
    }

    data class Graph(
        val nodes: Map<String, Node>,
        val adjacency: Map<String, List<Edge>>
    )

    fun buildGraph(): Graph {
        val nodes = LinkedHashMap<String, Node>()
        val steps = (RADIUS_M / GRID_STEP_M).toInt()

        for (row in -steps..steps) {
            for (col in -steps..steps) {
                val dNorth = row * GRID_STEP_M
                val dEast = col * GRID_STEP_M
                if (sqrt(dNorth * dNorth + dEast * dEast) > RADIUS_M) continue // 원형 범위로 자르기

                val (lat, lon) = offsetToLatLon(dNorth, dEast)
                val id = "${row}_$col"
                nodes[id] = Node(id, lat, lon)
            }
        }

        val adjacency = LinkedHashMap<String, MutableList<Edge>>()
        val directions = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0) // 상하좌우만 연결 (격자형 도로망 가정)

        for (row in -steps..steps) {
            for (col in -steps..steps) {
                val fromId = "${row}_$col"
                val fromNode = nodes[fromId] ?: continue

                for ((dr, dc) in directions) {
                    val toId = "${row + dr}_${col + dc}"
                    val toNode = nodes[toId] ?: continue

                    val distance = haversineMeters(fromNode.lat, fromNode.lon, toNode.lat, toNode.lon)
                    val safety = safetyScoreForSegment(fromNode, toNode)

                    adjacency.getOrPut(fromId) { mutableListOf() }.add(Edge(toId, distance, safety))
                }
            }
        }

        return Graph(nodes, adjacency)
    }

    /**
     * 두 노드 중간지점 기준으로 안전도 점수(0~1)를 계산.
     * isNight을 지정하지 않으면 현재 시각(LocalTime.now())으로 자동 판단한다 (06:00~18:00=주간).
     */
    fun safetyScoreForSegment(a: Node, b: Node, isNight: Boolean = isCurrentlyNight()): Double {
        val midLat = (a.lat + b.lat) / 2
        val midLon = (a.lon + b.lon) / 2

        var score = 0.85 // 기본 안전도

        // 위험구역 영향
        for (zone in dangerZones) {
            val (zoneLat, zoneLon) = offsetToLatLon(zone.latOffsetM, zone.lonOffsetM)
            val dist = haversineMeters(midLat, midLon, zoneLat, zoneLon)
            if (dist < zone.radiusM) {
                val falloff = 1.0 - (dist / zone.radiusM) // 중심에 가까울수록 1에 근접
                score -= zone.severity * falloff
            }
        }

        // 재현 가능한 약한 의사난수 노이즈 (좌표 기반 해시)
        val noise = pseudoNoise(midLat, midLon) * 0.1
        score += noise

        // 실제 편의점/지구대·파출소가 가까이 있으면 가산점 (거리 비례 감쇠)
        score += facilityBonus(midLat, midLon)

        // 개수 기반 가산점 (반경 내 안전시설이 몇 개 몰려있는지)
        score += facilityCountBonus(midLat, midLon)

        // 개방감 지수 (브이월드 건물 데이터 기반, 시간대 무관하게 항상 적용)
        val openness = VWorldBuildingProvider.computeOpennessNorm(midLat, midLon, buildings)
        score += OPENNESS_WEIGHT * openness

        // 조도 항: 야간=보안등 밀도, 주간=개방감으로 근사 (실측 일조 데이터 아님, 향후 개선 여지)
        score += if (isNight) {
            NIGHT_LIGHT_WEIGHT * StreetlightProvider.computeNightLightNorm(midLat, midLon, streetlights)
        } else {
            DAY_LIGHT_WEIGHT * openness
        }

        return score.coerceIn(0.05, 1.0)
    }

    private fun isCurrentlyNight(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 6 || hour >= 18
    }

    /** 가장 가까운 안전시설까지의 거리에 비례해 가산점을 계산한다. 150m 밖이면 가산점 없음. */
    private fun facilityBonus(midLat: Double, midLon: Double): Double {
        if (facilityPoints.isEmpty()) return 0.0

        val nearestDist = facilityPoints.minOf { (lat, lon) -> haversineMeters(midLat, midLon, lat, lon) }
        if (nearestDist > FACILITY_BOOST_RADIUS_M) return 0.0

        val falloff = 1.0 - (nearestDist / FACILITY_BOOST_RADIUS_M)
        return FACILITY_BOOST_AMOUNT * falloff
    }

    /** 반경 내 안전시설 "개수"에 비례해 가산점을 계산한다. 3개 이상이면 만점. */
    private fun facilityCountBonus(midLat: Double, midLon: Double): Double {
        if (facilityPoints.isEmpty()) return 0.0

        val count = facilityPoints.count { (lat, lon) ->
            haversineMeters(midLat, midLon, lat, lon) <= FACILITY_COUNT_RADIUS_M
        }
        return FACILITY_COUNT_WEIGHT * min(1.0, count.toDouble() / FACILITY_COUNT_REF)
    }

    private fun pseudoNoise(lat: Double, lon: Double): Double {
        val h = sin(lat * 12345.6789) * cos(lon * 9876.5432)
        return h - floor(h) - 0.5 // -0.5 ~ 0.5 범위
    }

    private fun offsetToLatLon(dNorthM: Double, dEastM: Double): Pair<Double, Double> {
        val dLat = dNorthM / 111_320.0
        val dLon = dEastM / (111_320.0 * cos(Math.toRadians(CENTER_LAT)))
        return (CENTER_LAT + dLat) to (CENTER_LON + dLon)
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /** 그래프 상에서 주어진 위경도와 가장 가까운 노드를 찾는다. */
    fun nearestNode(graph: Graph, lat: Double, lon: Double): Node? {
        return graph.nodes.values.minByOrNull { haversineMeters(it.lat, it.lon, lat, lon) }
    }
}
