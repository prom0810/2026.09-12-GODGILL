package com.safewalk.common.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var previousBaseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        previousBaseUrl = ApiClient.baseUrl
        ApiClient.baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        ApiClient.baseUrl = previousBaseUrl
        server.shutdown()
    }

    @Test
    fun `multipart request sends jwt fields and images then unwraps data`() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"success":true,"data":{"reportId":12},"message":null}"""),
        )

        val result = ApiClient.postMultipart(
            path = "/api/risk-reports",
            fields = mapOf("riskType" to "DARK_ROAD", "lat" to "36.8"),
            files = listOf(
                ApiClient.MultipartPart("images", "road.jpg", "image/jpeg", "jpeg-data".toByteArray()),
            ),
            accessToken = "jwt-token",
        ) as JSONObject

        assertEquals(12L, result.getLong("reportId"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("Bearer jwt-token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"riskType\""))
        assertTrue(body.contains("DARK_ROAD"))
        assertTrue(body.contains("filename=\"road.jpg\""))
        assertTrue(body.contains("jpeg-data"))
    }
}
