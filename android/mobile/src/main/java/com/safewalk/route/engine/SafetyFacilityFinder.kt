package com.safewalk.route.engine

import com.safewalk.route.model.Node

/**
 * 계산된 안전 경로 주변에 있는 편의점·지구대/파출소·CCTV를 찾는다.
 * 초창기 프로토타입 설계(안전시설 필터 칩: 편의점/치안시설)를 MVP 수준으로 구현한 버전.
 * CCTV는 팀원이 검증한 충남 빅데이터 CSV([CctvProvider])를 사용한다.
 */
object SafetyFacilityFinder {

    data class Facility(
        val name: String,
        val lat: Double,
        val lon: Double,
        val type: FacilityType
    )

    enum class FacilityType(val label: String) {
        CONVENIENCE_STORE("편의점"),
        POLICE("지구대·파출소"),
        CCTV("CCTV")
    }

    // 경로 노드로부터 이 거리(m) 이내에 있는 시설만 "경로 주변"으로 인정
    private const val NEAR_ROUTE_THRESHOLD_M = 200.0
    private const val SEARCH_RADIUS_M = 900

    suspend fun findNearbyFacilities(pathNodes: List<Node>): List<Facility> {
        if (pathNodes.isEmpty()) return emptyList()

        val centerLat = pathNodes.map { it.lat }.average()
        val centerLon = pathNodes.map { it.lon }.average()

        val stores = runCatching {
            KakaoApiClient.searchCategory("CS2", centerLat, centerLon, SEARCH_RADIUS_M)
        }.getOrDefault(emptyList())

        // 카카오 로컬 검색은 "경찰서" 전용 카테고리 코드가 없어 키워드 검색을 사용
        val policeRaw = runCatching {
            KakaoApiClient.searchKeyword("지구대", centerLat, centerLon, SEARCH_RADIUS_M) +
                KakaoApiClient.searchKeyword("파출소", centerLat, centerLon, SEARCH_RADIUS_M)
        }.getOrDefault(emptyList())

        val cctvRaw = runCatching {
            CctvProvider.findAllCctvInArea(centerLat, centerLon, SEARCH_RADIUS_M)
        }.getOrDefault(emptyList())

        val storeFacilities = stores
            .filter { isNearPath(it.lat, it.lon, pathNodes) }
            .map { Facility(it.placeName, it.lat, it.lon, FacilityType.CONVENIENCE_STORE) }

        val policeFacilities = policeRaw
            .distinctBy { "${it.placeName}_${it.lat}_${it.lon}" }
            .filter { isNearPath(it.lat, it.lon, pathNodes) }
            .map { Facility(it.placeName, it.lat, it.lon, FacilityType.POLICE) }

        val cctvFacilities = cctvRaw
            .filter { isNearPath(it.lat, it.lon, pathNodes) }
            .map { Facility(it.address.ifBlank { "CCTV" }, it.lat, it.lon, FacilityType.CCTV) }

        return storeFacilities + policeFacilities + cctvFacilities
    }

    private fun isNearPath(lat: Double, lon: Double, pathNodes: List<Node>): Boolean {
        return pathNodes.any {
            SafetyDataProvider.haversineMeters(it.lat, it.lon, lat, lon) <= NEAR_ROUTE_THRESHOLD_M
        }
    }

    /**
     * MVP 대상지역 전체(기본 1.5km 반경)의 편의점·지구대·파출소·CCTV 좌표를 불러온다.
     * 경로 표시용이 아니라 [SafetyDataProvider]의 안전도 점수 계산(가산점)에 쓰기 위한 것으로,
     * 앱 시작 시 한 번만 호출한다. 편의점은 도심에 많아 3페이지(최대 45곳)까지 모은다.
     */
    suspend fun findAllFacilitiesInArea(centerLat: Double, centerLon: Double, radiusMeters: Int = 1500): List<Pair<Double, Double>> {
        val stores = runCatching {
            (1..3).flatMap { page ->
                KakaoApiClient.searchCategory("CS2", centerLat, centerLon, radiusMeters, page = page)
            }
        }.getOrDefault(emptyList())

        val police = runCatching {
            KakaoApiClient.searchKeyword("지구대", centerLat, centerLon, radiusMeters) +
                KakaoApiClient.searchKeyword("파출소", centerLat, centerLon, radiusMeters)
        }.getOrDefault(emptyList())

        val cctv = runCatching {
            CctvProvider.findAllCctvInArea(centerLat, centerLon, radiusMeters)
        }.getOrDefault(emptyList())

        val kakaoPoints = (stores + police)
            .distinctBy { "${it.placeName}_${it.lat}_${it.lon}" }
            .map { it.lat to it.lon }

        val cctvPoints = cctv.map { it.lat to it.lon }

        return kakaoPoints + cctvPoints
    }
}
