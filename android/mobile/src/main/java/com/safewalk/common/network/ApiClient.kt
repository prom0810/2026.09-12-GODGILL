package com.safewalk.common.network

import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

/**
 * 백엔드 Spring Boot 서버 접속 설정과 최소한의 HTTP 클라이언트.
 * 기존 Kakao API 호출(KakaoPlaceSearch, NearbyFacilitySearch)과 동일하게
 * HttpURLConnection을 직접 사용한다 — Retrofit 등 별도 통신 라이브러리는 추가하지 않았다.
 *
 * 응답은 백엔드의 공통 포맷 { success, data, message }를 그대로 따른다고 가정하고,
 * 성공 시 "data" 객체만 꺼내서 돌려준다.
 */
object ApiClient {
    /**
     * 개발 중인 백엔드 서버 주소.
     * - 에뮬레이터 표준 호스트 별칭인 10.0.2.2와 `adb reverse` 터널 둘 다 이 PC 환경에서는
     *   원인 불명의 이유로 막혀 있었다(전자는 10초 connect 타임아웃, 후자는 즉시 connection
     *   refused — 앱뿐 아니라 에뮬레이터 내 크롬으로도 동일하게 재현됨). 대신 PC의 실제
     *   네트워크 어댑터 IP(`ipconfig`로 확인)를 직접 사용해서 우회했다.
     * - ⚠️ 이 값은 PC/네트워크마다 다르고 DHCP로 바뀔 수 있다. 다른 개발자 PC나 재부팅 후
     *   연결이 안 되면 `ipconfig`로 현재 IP를 다시 확인해서 이 값을 갱신할 것.
     *   (장기적으로는 이 값을 소스에 하드코딩하지 말고 local.properties → BuildConfig
     *   방식(Kakao 키와 동일한 패턴)으로 옮기는 걸 권장 — 그러면 개인 설정이 커밋되지 않는다.)
     * - HTTPS가 아닌 개발 서버이므로 디버그 빌드에서는 평문 HTTP 통신이 허용되어 있어야 한다
     *   (android:usesCleartextTraffic="true", AndroidManifest.xml에 이미 설정됨).
     */
    var baseUrl: String = "http://192.168.56.1:8080"

    class ApiException(val statusCode: Int, message: String) : IOException(message)

    fun postJson(path: String, body: JSONObject): JSONObject {
        // Proxy.NO_PROXY: 시스템/JVM에 프록시가 잡혀 있어도 우회해서 직접 연결을 시도한다.
        val connection = URL(baseUrl + path).openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val envelope = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)

            if (status !in 200..299) {
                val message = envelope.optString("message")
                    .ifBlank { "서버 요청에 실패했습니다. (HTTP $status)" }
                throw ApiException(status, message)
            }
            return envelope.optJSONObject("data") ?: JSONObject()
        } finally {
            connection.disconnect()
        }
    }
}
