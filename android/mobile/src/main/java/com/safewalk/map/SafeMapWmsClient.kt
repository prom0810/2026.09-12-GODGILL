package com.safewalk.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class WmsBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

class SafeMapWmsClient(private val serviceKey: String) {
    fun load(bounds: WmsBounds, width: Int, height: Int): Bitmap {
        require(serviceKey.isNotBlank()) { "SafeMap service key is missing" }
        require(width > 0 && height > 0) { "WMS image size must be positive" }

        val query = linkedMapOf(
            "serviceKey" to decodedServiceKey(),
            "srs" to "EPSG:4326",
            "bbox" to String.format(
                Locale.US,
                "%.8f,%.8f,%.8f,%.8f",
                bounds.west,
                bounds.south,
                bounds.east,
                bounds.north,
            ),
            "format" to "image/png",
            "width" to width.toString(),
            "height" to height.toString(),
            "transparent" to "TRUE",
        ).entries.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
        val connection = URI("https://www.safemap.go.kr/openapi2/IF_0080_WMS?$query")
            .toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "image/png")
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("SafeMap WMS returned HTTP $status")
            }
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)
                    ?: throw IOException("SafeMap WMS image could not be decoded")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.name(),
    ).replace("+", "%20")

    private fun decodedServiceKey(): String = if ('%' in serviceKey) {
        URLDecoder.decode(serviceKey, StandardCharsets.UTF_8.name())
    } else {
        serviceKey
    }
}
