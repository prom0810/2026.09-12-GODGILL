package com.foresto.gatgil.route

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * 행정안전부 생활안전지도(SafeMap) WMS로 위험구역을 이미지(래스터) 한 장으로 받아온다.
 * (팀원이 검증해 둔 구현을 이식 — 원본: com.safewalk.map.SafeMapWmsClient)
 *
 * ⚠️ 중요: 이 API는 좌표별 위험도 "수치"가 아니라 지정한 영역 전체를 그린 PNG 이미지 한 장을
 * 반환한다. 그래서 AStarRouter의 안전도 계산(구간별 숫자가 필요)에는 직접 못 쓰고,
 * 지도 위에 "켜고 끌 수 있는 시각적 오버레이"로만 사용한다. 안전도 점수는 지금처럼
 * 목업 위험구역 + 안전시설 + 개방감 + 조도로 계산되고, 이 오버레이는 그 계산 결과를
 * 사용자가 눈으로 참고/대조하는 용도다.
 */
object SafeMapWmsClient {

    data class WmsBounds(val west: Double, val south: Double, val east: Double, val north: Double)

    suspend fun load(bounds: WmsBounds, width: Int, height: Int, serviceKey: String): Bitmap? =
        withContext(Dispatchers.IO) {
            if (serviceKey.isBlank() || width <= 0 || height <= 0) return@withContext null

            runCatching {
                val query = linkedMapOf(
                    "serviceKey" to decodedServiceKey(serviceKey),
                    "srs" to "EPSG:4326",
                    "bbox" to String.format(
                        Locale.US, "%.8f,%.8f,%.8f,%.8f",
                        bounds.west, bounds.south, bounds.east, bounds.north
                    ),
                    "format" to "image/png",
                    "width" to width.toString(),
                    "height" to height.toString(),
                    "transparent" to "TRUE"
                ).entries.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }

                val connection = (URL("https://www.safemap.go.kr/openapi2/IF_0080_WMS?$query")
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "image/png")
                }

                val status = connection.responseCode
                if (status !in 200..299) {
                    connection.disconnect()
                    throw IOException("SafeMap WMS 오류($status)")
                }

                val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                connection.disconnect()
                bitmap ?: throw IOException("SafeMap WMS 이미지를 디코딩하지 못했습니다")
            }.getOrNull()
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun decodedServiceKey(serviceKey: String): String =
        if ('%' in serviceKey) URLDecoder.decode(serviceKey, "UTF-8") else serviceKey
}
