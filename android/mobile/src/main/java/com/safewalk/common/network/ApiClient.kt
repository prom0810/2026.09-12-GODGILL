package com.safewalk.common.network

import android.content.Context
import com.safewalk.BuildConfig
import com.safewalk.auth.TokenStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.UUID

/**
 * 백엔드 Spring Boot 서버 접속 설정과 최소한의 HTTP 클라이언트.
 * 기존 Kakao API 호출(KakaoPlaceSearch, NearbyFacilitySearch)과 동일하게
 * HttpURLConnection을 직접 사용한다 — Retrofit 등 별도 통신 라이브러리는 추가하지 않았다.
 *
 * 응답은 백엔드의 공통 포맷 { success, data, message }를 그대로 따른다고 가정하고,
 * 성공 시 "data" 객체만 꺼내서 돌려준다.
 */
object ApiClient {
    @Volatile
    var baseUrl: String = BuildConfig.BACKEND_BASE_URL.trimEnd('/')

    class ApiException(val statusCode: Int, message: String) : IOException(message)

    fun get(path: String, accessToken: String? = null): Any? =
        request("GET", path, accessToken = accessToken)

    fun post(path: String, body: JSONObject? = null, accessToken: String? = null): Any? =
        request("POST", path, body, accessToken)

    fun put(path: String, body: JSONObject? = null, accessToken: String? = null): Any? =
        request("PUT", path, body, accessToken)

    fun patch(path: String, body: JSONObject? = null, accessToken: String? = null): Any? =
        request("PATCH", path, body, accessToken)

    fun delete(path: String, body: JSONObject? = null, accessToken: String? = null): Any? =
        request("DELETE", path, body, accessToken)

    data class MultipartPart(
        val fieldName: String,
        val fileName: String,
        val contentType: String,
        val bytes: ByteArray,
    )

    fun postMultipart(
        path: String,
        fields: Map<String, String>,
        files: List<MultipartPart> = emptyList(),
        accessToken: String? = null,
    ): Any? {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val boundary = "SafeWalk-${UUID.randomUUID()}"
        val connection = URL(baseUrl + normalizedPath).openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            if (!accessToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            connection.outputStream.buffered().use { output ->
                fields.forEach { (name, value) ->
                    output.write("--$boundary\r\n".toByteArray())
                    output.write("Content-Disposition: form-data; name=\"${name.replace("\"", "")}\"\r\n\r\n".toByteArray())
                    output.write(value.toByteArray(Charsets.UTF_8))
                    output.write("\r\n".toByteArray())
                }
                files.forEach { file ->
                    output.write("--$boundary\r\n".toByteArray())
                    output.write(
                        (
                            "Content-Disposition: form-data; name=\"${file.fieldName.replace("\"", "")}\"; " +
                                "filename=\"${file.fileName.replace("\"", "")}\"\r\n"
                        ).toByteArray(),
                    )
                    output.write("Content-Type: ${file.contentType}\r\n\r\n".toByteArray())
                    output.write(file.bytes)
                    output.write("\r\n".toByteArray())
                }
                output.write("--$boundary--\r\n".toByteArray())
            }
            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    /** 기존 인증 API 호출부 호환용. */
    fun postJson(path: String, body: JSONObject): JSONObject =
        post(path, body) as? JSONObject ?: JSONObject()

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null,
    ): Any? {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val connection = URL(baseUrl + normalizedPath).openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            if (!accessToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }

            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(connection: HttpURLConnection): Any? {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val envelope = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        if (status !in 200..299 || (envelope.has("success") && !envelope.optBoolean("success"))) {
            val message = envelope.optString("message")
                .ifBlank { "서버 요청에 실패했습니다. (HTTP $status)" }
            throw ApiException(status, message)
        }
        if (!envelope.has("data") || envelope.isNull("data")) return null
        return envelope.get("data")
    }
}

/** 저장된 JWT를 보호 API에 붙이고, 401이면 만료된 로컬 세션을 정리한다. */
class AuthenticatedApiClient(context: Context) {
    private val tokenStore = TokenStore(context)

    fun getObject(path: String): JSONObject = execute { ApiClient.get(path, it) }.asObject()
    fun getArray(path: String): JSONArray = execute { ApiClient.get(path, it) }.asArray()
    fun postObject(path: String, body: JSONObject? = null): JSONObject =
        execute { ApiClient.post(path, body, it) }.asObject()
    fun post(path: String, body: JSONObject? = null) { execute { ApiClient.post(path, body, it) } }
    fun putObject(path: String, body: JSONObject): JSONObject =
        execute { ApiClient.put(path, body, it) }.asObject()
    fun patchObject(path: String, body: JSONObject? = null): JSONObject =
        execute { ApiClient.patch(path, body, it) }.asObject()
    fun patch(path: String, body: JSONObject? = null) { execute { ApiClient.patch(path, body, it) } }
    fun delete(path: String, body: JSONObject? = null) { execute { ApiClient.delete(path, body, it) } }
    fun postMultipartObject(
        path: String,
        fields: Map<String, String>,
        files: List<ApiClient.MultipartPart> = emptyList(),
    ): JSONObject = execute { ApiClient.postMultipart(path, fields, files, it) }.asObject()

    private fun execute(block: (String) -> Any?): Any? {
        val token = tokenStore.accessToken()
            ?: throw ApiClient.ApiException(401, "로그인이 필요합니다.")
        return try {
            block(token)
        } catch (error: ApiClient.ApiException) {
            if (error.statusCode == 401) tokenStore.clear()
            throw error
        }
    }

    private fun Any?.asObject(): JSONObject = this as? JSONObject ?: JSONObject()
    private fun Any?.asArray(): JSONArray = this as? JSONArray ?: JSONArray()
}
