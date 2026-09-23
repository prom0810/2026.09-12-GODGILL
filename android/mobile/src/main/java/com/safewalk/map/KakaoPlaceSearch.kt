package com.safewalk.map

import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

data class Place(val name: String, val address: String, val latitude: Double, val longitude: Double) {
    override fun toString(): String = "$name\n$address"
}

class PlaceSearchException(val statusCode: Int) : IOException()

class KakaoPlaceSearch(private val apiKey: String) {
    fun search(query: String): List<Place> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = URL("https://dapi.kakao.com/v2/local/search/keyword.json?query=$encoded&size=15")
            .openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Authorization", "KakaoAK $apiKey")
            val code = connection.responseCode
            if (code != 200) throw PlaceSearchException(code)
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val documents = JSONObject(body).getJSONArray("documents")
            return List(documents.length()) { index ->
                val item = documents.getJSONObject(index)
                Place(item.getString("place_name"),
                    item.optString("road_address_name").ifBlank { item.optString("address_name") },
                    item.getString("y").toDouble(), item.getString("x").toDouble())
            }
        } finally {
            connection.disconnect()
        }
    }
}
