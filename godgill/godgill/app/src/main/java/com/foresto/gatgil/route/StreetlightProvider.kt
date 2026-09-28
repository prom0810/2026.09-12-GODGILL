package com.foresto.gatgil.route

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * 보안등(가로등) 데이터로 야간 조도 항(N)을 계산한다.
 *
 * 팀원이 확보한 충청남도 천안시 보안등정보 CSV(공공데이터포털 원본, 실측 데이터)를
 * assets에 번들해서 쓴다 — 네트워크 API 호출 없이 항상 동작하고, 필드명도 실제
 * 파일 기준으로 검증됐다 (이전 버전은 data.go.kr API를 추정 파라미터로 호출하려 했으나,
 * 이 방식이 더 안정적이라 교체함).
 *
 * CSV 헤더: 보안등위치명,설치개수,소재지도로명주소,소재지지번주소,위도,경도,설치연도,설치형태,관리기관전화번호,관리기관명,데이터기준일자
 */
object StreetlightProvider {

    private const val ASSET_FILE = "보안등정보_천안시.csv"

    private const val NIGHT_MATCH_RADIUS_M = 30.0
    private const val NIGHT_NORMALIZE_COUNT = 3 // 이 개수 이상이면 만점(1.0)

    /**
     * assets의 CSV를 읽어 centerLat/centerLon 반경 이내 보안등 좌표만 걸러서 반환한다.
     * 설치개수(1개 지점에 여러 등이 설치된 경우) 컬럼은 지금은 좌표 중복도로만 반영한다
     * (지점을 설치개수만큼 복제하지 않고 좌표 1개로 취급 — 너무 촘촘한 점을 과대평가하지 않기 위함).
     */
    suspend fun findAllStreetlightsInArea(
        context: Context,
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int = 1500
    ): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        runCatching {
            val results = mutableListOf<Pair<Double, Double>>()

            context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).useLines { lines ->
                val iterator = lines.iterator()
                if (!iterator.hasNext()) return@useLines

                val header = parseCsvLine(iterator.next())
                    .mapIndexed { index, name -> name.removePrefix("\uFEFF") to index }
                    .toMap()
                val latIndex = header["위도"] ?: return@useLines
                val lonIndex = header["경도"] ?: return@useLines

                while (iterator.hasNext()) {
                    val columns = parseCsvLine(iterator.next())
                    if (columns.size <= maxOf(latIndex, lonIndex)) continue

                    val lat = columns[latIndex].toDoubleOrNull() ?: continue
                    val lon = columns[lonIndex].toDoubleOrNull() ?: continue

                    if (SafetyDataProvider.haversineMeters(centerLat, centerLon, lat, lon) <= radiusMeters) {
                        results.add(lat to lon)
                    }
                }
            }

            results
        }.getOrDefault(emptyList())
    }

    /** N_norm(e): 구간 중점 30m 이내 보안등 개수를 3개 기준으로 0~1 정규화. */
    fun computeNightLightNorm(midLat: Double, midLon: Double, streetlights: List<Pair<Double, Double>>): Double {
        if (streetlights.isEmpty()) return 0.0

        val nearbyCount = streetlights.count { (lat, lon) ->
            SafetyDataProvider.haversineMeters(midLat, midLon, lat, lon) <= NIGHT_MATCH_RADIUS_M
        }
        return min(1.0, nearbyCount.toDouble() / NIGHT_NORMALIZE_COUNT)
    }

    /** 따옴표로 감싸진 필드를 지원하는 간단한 CSV 파서 (팀원 구현 그대로 사용). */
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
