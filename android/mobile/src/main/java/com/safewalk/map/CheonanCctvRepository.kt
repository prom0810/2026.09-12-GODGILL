package com.safewalk.map

import java.net.URL
import java.nio.charset.Charset
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CctvSite(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val purpose: String,
    val cameraCount: Int,
)

class CheonanCctvRepository {
    fun loadNearCheonanStation(): List<CctvSite> {
        val connection = URL(CSV_URL).openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("CCTV CSV returned HTTP ${connection.responseCode}")
            }
            val lines = connection.inputStream.bufferedReader(Charset.forName("MS949"))
                .use { it.readLines() }
            if (lines.isEmpty()) return emptyList()
            val header = parseCsvLine(lines.first()).mapIndexed { index, name -> name to index }.toMap()
            val latitudeIndex = header.getValue("WGS84위도")
            val longitudeIndex = header.getValue("WGS84경도")
            val roadAddressIndex = header.getValue("소재지도로명주소")
            val lotAddressIndex = header.getValue("소재지지번주소")
            val purposeIndex = header.getValue("설치목적구분")
            val countIndex = header.getValue("카메라대수")
            val grouped = linkedMapOf<String, MutableCctvSite>()
            lines.drop(1).forEach { line ->
                val columns = parseCsvLine(line)
                if (columns.size <= maxOf(latitudeIndex, longitudeIndex, countIndex)) return@forEach
                val latitude = columns[latitudeIndex].toDoubleOrNull() ?: return@forEach
                val longitude = columns[longitudeIndex].toDoubleOrNull() ?: return@forEach
                if (distanceKm(STATION_LATITUDE, STATION_LONGITUDE, latitude, longitude) > RADIUS_KM) {
                    return@forEach
                }
                val key = String.format(Locale.US, "%.7f,%.7f", latitude, longitude)
                val site = grouped.getOrPut(key) {
                    MutableCctvSite(
                        latitude,
                        longitude,
                        columns[roadAddressIndex].ifBlank { columns[lotAddressIndex] },
                        columns[purposeIndex],
                    )
                }
                site.cameraCount += columns[countIndex].toIntOrNull() ?: 1
            }
            grouped.values.map { it.toCctvSite() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    value.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    values += value.toString()
                    value.clear()
                }
                else -> value.append(character)
            }
            index++
        }
        values += value.toString()
        return values
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latitudeDistance = Math.toRadians(lat2 - lat1)
        val longitudeDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latitudeDistance / 2) * sin(latitudeDistance / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(longitudeDistance / 2) * sin(longitudeDistance / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    private data class MutableCctvSite(
        val latitude: Double,
        val longitude: Double,
        val address: String,
        val purpose: String,
        var cameraCount: Int = 0,
    ) {
        fun toCctvSite() = CctvSite(latitude, longitude, address, purpose, cameraCount)
    }

    companion object {
        private const val CSV_URL =
            "https://alldam.chungnam.go.kr/bigdata/collect/file/download.do?fileSid=5166"
        private const val STATION_LATITUDE = 36.8100
        private const val STATION_LONGITUDE = 127.1467
        private const val RADIUS_KM = 1.5
        private const val EARTH_RADIUS_KM = 6371.0
    }
}
