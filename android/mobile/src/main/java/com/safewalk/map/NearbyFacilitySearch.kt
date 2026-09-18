package com.safewalk.map

import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

enum class FacilityType { SECURITY, CONVENIENCE_STORE, FIRE }

data class Facility(
    val id: String,
    val type: FacilityType,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

class NearbyFacilitySearch(private val apiKey: String) {
    fun search(bounds: WmsBounds): List<Facility> {
        val facilities = buildList {
        addAll(searchKeyword("경찰", FacilityType.SECURITY, bounds))
        addAll(searchCategory("CS2", FacilityType.CONVENIENCE_STORE, bounds))
        addAll(searchKeyword("소방", FacilityType.FIRE, bounds))
        }.distinctBy { it.id.ifBlank { "${it.type}:${it.latitude}:${it.longitude}" } }
        val centerLatitude = (bounds.south + bounds.north) / 2
        val centerLongitude = (bounds.west + bounds.east) / 2
        val stores = facilities.asSequence()
            .filter { it.type == FacilityType.CONVENIENCE_STORE }
            .sortedBy {
                val latitudeOffset = it.latitude - centerLatitude
                val longitudeOffset = it.longitude - centerLongitude
                latitudeOffset * latitudeOffset + longitudeOffset * longitudeOffset
            }
            .take(MAX_CONVENIENCE_STORES)
        return facilities.filter { it.type != FacilityType.CONVENIENCE_STORE } + stores
    }

    private fun searchKeyword(
        query: String,
        type: FacilityType,
        bounds: WmsBounds,
    ): List<Facility> = request(
        path = "keyword.json",
        parameters = mapOf("query" to query),
        type = type,
        bounds = bounds,
    )

    private fun searchCategory(
        categoryCode: String,
        type: FacilityType,
        bounds: WmsBounds,
    ): List<Facility> = request(
        path = "category.json",
        parameters = mapOf("category_group_code" to categoryCode),
        type = type,
        bounds = bounds,
    )

    private fun request(
        path: String,
        parameters: Map<String, String>,
        type: FacilityType,
        bounds: WmsBounds,
    ): List<Facility> {
        val results = mutableListOf<Facility>()
        for (page in 1..3) {
            val rect = String.format(
                Locale.US,
                "%.8f,%.8f,%.8f,%.8f",
                bounds.west,
                bounds.south,
                bounds.east,
                bounds.north,
            )
            val query = (parameters + mapOf(
                "rect" to rect,
                "page" to page.toString(),
                "size" to "15",
            )).entries.joinToString("&") { (name, value) ->
                "${encode(name)}=${encode(value)}"
            }
            val connection = URL("https://dapi.kakao.com/v2/local/search/$path?$query")
                .openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Authorization", "KakaoAK $apiKey")
                val status = connection.responseCode
                if (status != 200) throw PlaceSearchException(status)
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(body)
                val documents = root.getJSONArray("documents")
                repeat(documents.length()) { index ->
                    val item = documents.getJSONObject(index)
                    results += Facility(
                        id = item.optString("id"),
                        type = type,
                        name = item.getString("place_name"),
                        address = item.optString("road_address_name")
                            .ifBlank { item.optString("address_name") },
                        latitude = item.getString("y").toDouble(),
                        longitude = item.getString("x").toDouble(),
                    )
                }
                if (root.getJSONObject("meta").getBoolean("is_end")) break
            } finally {
                connection.disconnect()
            }
        }
        return results
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val MAX_CONVENIENCE_STORES = 20
    }
}
