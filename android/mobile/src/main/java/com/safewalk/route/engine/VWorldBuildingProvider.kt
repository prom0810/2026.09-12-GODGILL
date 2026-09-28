package com.safewalk.route.engine

import com.safewalk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 브이월드(V-World) 데이터API로 대상지역 전체의 건물(위치+높이)을 한 번 불러와 캐시한다.
 * 구간별 "개방감 지수" 계산은 이 캐시에서 거리 필터링만 하므로,
 * 안전시설(facilityPoints)·보안등(streetlights)과 동일한 패턴이다.
 *
 * O(e) = 가장 가까운 건물까지 거리 / 주변 건물 평균 높이
 * 값이 클수록(건물 낮고 멀리 떨어짐) 트인 느낌 -> 안전도 가산
 *
 * ⚠️ 주의: 브이월드 데이터API의 정확한 레이어명(LAYER_NAME)·높이 필드명(HEIGHT_FIELD)은
 * 이 코드 작성 시점에 vworld.kr 개발자센터 문서로 100% 확정하지 못했다.
 * 인증키 발급 후 https://www.vworld.kr/dev/v4dv_wfsguide2_s001.do 에서
 * "건물통합정보" 레이어의 정확한 data 파라미터명과 높이 속성명을 확인해
 * 아래 두 상수를 맞춰야 실제로 동작한다. (지금은 뼈대만 구현된 상태 — 실패 시
 * 자동으로 빈 리스트를 반환하므로 앱이 깨지지는 않고, 개방감 항만 0으로 처리된다.)
 */
object VWorldBuildingProvider {

    private const val DATA_API_URL = "https://api.vworld.kr/req/data"

    // TODO: 실제 문서 확인 후 정확한 값으로 교체
    private const val LAYER_NAME = "LT_C_BULDS"   // 건물통합정보(추정) - 문서 대조 필요
    private const val HEIGHT_FIELD = "hg"         // 높이 필드명(추정) - 문서 대조 필요
    private const val GROUND_FLOOR_FIELD = "gfa_flr" // 지상층수 필드명(추정) - 문서 대조 필요, 높이 없을 때 대체용

    // 한국 건축물 평균 층고(m). 높이 데이터가 없고 층수만 있을 때 추정에 사용.
    private const val AVG_FLOOR_HEIGHT_M = 3.0

    data class BuildingPoint(val lat: Double, val lon: Double, val heightM: Double, val isEstimated: Boolean)

    private const val OPENNESS_MATCH_RADIUS_M = 30.0
    private const val OPENNESS_REFERENCE = 1.2 // 거리/높이 비율이 이 값 이상이면 "충분히 트임"
    private const val MIN_SAMPLE_COUNT = 2     // 이보다 표본이 적으면 신뢰할 수 없다고 보고 0 처리

    /** 마지막으로 불러온 건물 데이터의 품질 통계 (로그/디버깅·논문용 커버리지 서술에 사용). */
    data class LoadStats(val total: Int, val withRealHeight: Int, val withEstimatedHeight: Int, val skipped: Int)

    var lastLoadStats: LoadStats? = null
        private set

    /**
     * 대상지역 전체 건물 목록(위치+높이)을 불러온다.
     * size 파라미터로 페이지당 최대치만 받으며(도심 밀집지역은 건물 수가 많아 MVP에서는
     * 1페이지만 사용), 실패 시 빈 리스트를 반환해 개방감 항이 자동으로 무효화되게 한다.
     */
    suspend fun findAllBuildingsInArea(centerLat: Double, centerLon: Double, radiusMeters: Int = 1500): List<BuildingPoint> =
        withContext(Dispatchers.IO) {
            runCatching {
                val urlStr = "$DATA_API_URL?service=data&request=GetFeature&format=json" +
                    "&size=1000&page=1&data=$LAYER_NAME" +
                    "&geomFilter=POINT($centerLon %20 $centerLat)&buffer=$radiusMeters" +
                    "&key=${BuildConfig.VWORLD_API_KEY}&domain=localhost"

                val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (responseCode !in 200..299) {
                    throw IOException("브이월드 API 오류($responseCode): $body")
                }

                parseBuildings(body)
            }.getOrDefault(emptyList())
        }

    private fun parseBuildings(json: String): List<BuildingPoint> {
        val root = JSONObject(json)
        val features = root
            .optJSONObject("response")
            ?.optJSONObject("result")
            ?.optJSONObject("featureCollection")
            ?.optJSONArray("features") ?: return emptyList()

        val results = mutableListOf<BuildingPoint>()
        var withRealHeight = 0
        var withEstimatedHeight = 0
        var skipped = 0

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val properties = feature.optJSONObject("properties") ?: continue

            val realHeight = properties.optDouble(HEIGHT_FIELD, Double.NaN)
            val height: Double
            val isEstimated: Boolean

            when {
                !realHeight.isNaN() && realHeight > 0 -> {
                    height = realHeight
                    isEstimated = false
                }
                else -> {
                    // 높이가 비어있으면 층수 × 평균 층고로 추정
                    val floors = properties.optInt(GROUND_FLOOR_FIELD, -1)
                    if (floors > 0) {
                        height = floors * AVG_FLOOR_HEIGHT_M
                        isEstimated = true
                    } else {
                        skipped++
                        continue // 높이도, 층수도 없으면 이 건물은 계산에서 완전히 제외
                    }
                }
            }

            val geometry = feature.optJSONObject("geometry") ?: continue
            val coords = geometry.optJSONArray("coordinates") ?: continue
            val firstPoint = extractFirstCoordinate(coords) ?: continue // (lon, lat)

            if (isEstimated) withEstimatedHeight++ else withRealHeight++
            results.add(BuildingPoint(lat = firstPoint.second, lon = firstPoint.first, heightM = height, isEstimated = isEstimated))
        }

        lastLoadStats = LoadStats(
            total = features.length(),
            withRealHeight = withRealHeight,
            withEstimatedHeight = withEstimatedHeight,
            skipped = skipped
        )

        return results
    }

    /** GeoJSON 중첩 배열([[[[lon,lat],...]]] 형태 등)에서 첫 번째 [lon, lat] 좌표를 재귀적으로 찾는다. */
    private fun extractFirstCoordinate(arr: JSONArray): Pair<Double, Double>? {
        if (arr.length() == 0) return null
        val first = arr.get(0)
        return if (first is JSONArray) {
            if (first.length() >= 2 && first.opt(0) is Number) {
                first.getDouble(0) to first.getDouble(1) // [lon, lat]
            } else {
                extractFirstCoordinate(first)
            }
        } else {
            null
        }
    }

    /**
     * 개방감 지수 O_norm(e). 구간 중점 30m 이내에 (실측이든 추정이든) 높이 데이터가 있는
     * 건물이 [MIN_SAMPLE_COUNT]개 미만이면 신뢰할 수 없다고 보고 0(영향 없음)을 반환한다.
     * 순수 함수(동기)라서 A* 그래프 빌드 중 구간마다 바로 호출 가능하다.
     */
    fun computeOpennessNorm(midLat: Double, midLon: Double, buildings: List<BuildingPoint>): Double {
        if (buildings.isEmpty()) return 0.0

        val nearby = buildings.filter {
            SafetyDataProvider.haversineMeters(midLat, midLon, it.lat, it.lon) <= OPENNESS_MATCH_RADIUS_M
        }
        if (nearby.size < MIN_SAMPLE_COUNT) return 0.0

        val avgHeight = nearby.map { it.heightM }.average()
        val nearestDist = nearby.minOf { SafetyDataProvider.haversineMeters(midLat, midLon, it.lat, it.lon) }
        if (avgHeight <= 0) return 0.0

        val openness = nearestDist / avgHeight
        return kotlin.math.min(1.0, openness / OPENNESS_REFERENCE)
    }
}
