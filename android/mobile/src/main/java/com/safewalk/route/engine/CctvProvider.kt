package com.safewalk.route.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/**
 * 충청남도 빅데이터 포털의 천안시 CCTV 설치현황 CSV를 내려받아 파싱한다.
 * (팀원이 검증해 둔 실제 다운로드 URL과 CSV 필드명을 그대로 사용)
 *
 * CSV 인코딩이 MS949(EUC-KR 계열)라는 점에 주의 — UTF-8로 읽으면 한글이 깨진다.
 */
object CctvProvider {

    data class CctvSite(
        val lat: Double,
        val lon: Double,
        val address: String,
        val purpose: String,
        val cameraCount: Int
    )

    private const val CSV_URL =
        "https://alldam.chungnam.go.kr/bigdata/collect/file/download.do?fileSid=5166"

    suspend fun findAllCctvInArea(centerLat: Double, centerLon: Double, radiusMeters: Int = 1500): List<CctvSite> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(CSV_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw java.io.IOException("CCTV CSV 오류($responseCode)")
                }

                val lines = connection.inputStream
                    .bufferedReader(Charset.forName("MS949"))
                    .use { it.readLines() }
                connection.disconnect()

                parseAndFilter(lines, centerLat, centerLon, radiusMeters.toDouble())
            }.getOrDefault(emptyList())
        }

    private fun parseAndFilter(lines: List<String>, centerLat: Double, centerLon: Double, radiusM: Double): List<CctvSite> {
        if (lines.isEmpty()) return emptyList()

        val header = parseCsvLine(lines.first()).mapIndexed { index, name -> name to index }.toMap()
        val latIndex = header["WGS84위도"] ?: return emptyList()
        val lonIndex = header["WGS84경도"] ?: return emptyList()
        val roadAddressIndex = header["소재지도로명주소"]
        val lotAddressIndex = header["소재지지번주소"]
        val purposeIndex = header["설치목적구분"]
        val countIndex = header["카메라대수"]

        val results = mutableListOf<CctvSite>()

        for (line in lines.drop(1)) {
            val columns = parseCsvLine(line)
            if (columns.size <= maxOf(latIndex, lonIndex)) continue

            val lat = columns.getOrNull(latIndex)?.toDoubleOrNull() ?: continue
            val lon = columns.getOrNull(lonIndex)?.toDoubleOrNull() ?: continue

            if (SafetyDataProvider.haversineMeters(centerLat, centerLon, lat, lon) > radiusM) continue

            val roadAddress = roadAddressIndex?.let { columns.getOrNull(it) }.orEmpty()
            val lotAddress = lotAddressIndex?.let { columns.getOrNull(it) }.orEmpty()
            val address = roadAddress.ifBlank { lotAddress }
            val purpose = purposeIndex?.let { columns.getOrNull(it) }.orEmpty()
            val count = countIndex?.let { columns.getOrNull(it)?.toIntOrNull() } ?: 1

            results.add(CctvSite(lat, lon, address, purpose, count))
        }

        return results
    }

    /** 따옴표로 감싸진 필드를 지원하는 간단한 CSV 파서. */
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
}
