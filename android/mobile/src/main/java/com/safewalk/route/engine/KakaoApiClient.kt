package com.safewalk.route.engine

import com.safewalk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 카카오맵 로컬 검색 API 클라이언트.
 *  - 키워드로 장소 검색: 출발지/도착지 텍스트 입력
 *  - 카테고리로 장소 검색: 경로 주변 편의점 등 안전시설 탐색
 */
object KakaoApiClient {

    private const val KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
    private const val CATEGORY_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/category.json"

    data class PlaceResult(
        val placeName: String,
        val addressName: String,
        val lat: Double, // y
        val lon: Double  // x
    )

    /** query로 장소를 검색해 최대 15개 결과를 반환한다. 정확도순(accuracy) 정렬. */
    suspend fun searchKeyword(
        query: String,
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int = 1500
    ): List<PlaceResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlStr = "$KEYWORD_SEARCH_URL?query=$encodedQuery&x=$centerLon&y=$centerLat&radius=$radiusMeters&size=15"
        fetchPlaces(urlStr)
    }

    /**
     * 카테고리 코드로 장소를 검색한다. (예: CS2=편의점, PS3=어린이집·유치원)
     * 전체 카테고리 코드는 카카오맵 API 문서 참고.
     */
    suspend fun searchCategory(
        categoryGroupCode: String,
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int = 900,
        page: Int = 1
    ): List<PlaceResult> = withContext(Dispatchers.IO) {
        val urlStr = "$CATEGORY_SEARCH_URL?category_group_code=$categoryGroupCode&x=$centerLon&y=$centerLat&radius=$radiusMeters&size=15&page=$page"
        fetchPlaces(urlStr)
    }

    private fun fetchPlaces(urlStr: String): List<PlaceResult> {
        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")
            connectTimeout = 8000
            readTimeout = 8000
        }

        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }

            if (responseCode !in 200..299) {
                throw IOException("카카오 API 오류($responseCode): $body")
            }

            val json = JSONObject(body)
            val documents = json.getJSONArray("documents")
            val results = mutableListOf<PlaceResult>()

            for (i in 0 until documents.length()) {
                val doc = documents.getJSONObject(i)
                val roadAddress = doc.optString("road_address_name")
                val address = if (roadAddress.isNotBlank()) roadAddress else doc.optString("address_name")

                results.add(
                    PlaceResult(
                        placeName = doc.getString("place_name"),
                        addressName = address,
                        lat = doc.getString("y").toDouble(),
                        lon = doc.getString("x").toDouble()
                    )
                )
            }
            return results
        } finally {
            connection.disconnect()
        }
    }
}
