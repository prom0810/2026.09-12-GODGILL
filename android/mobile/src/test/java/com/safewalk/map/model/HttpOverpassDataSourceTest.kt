package com.safewalk.map.model

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class HttpOverpassDataSourceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetch posts an encoded query and returns the response body`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"elements": []}"""),
        )
        val dataSource = HttpOverpassDataSource(maximumConnectTimeoutMillis = 1_000)

        val response = dataSource.fetch(
            endpoint = server.url("/interpreter").toString(),
            query = "[out:json];way(1);out;",
            timeoutMillis = 2_000,
        )

        assertEquals("""{"elements": []}""", response)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/interpreter", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.body.readUtf8().startsWith("data=%5Bout%3Ajson%5D"))
    }

    @Test
    fun `non success response includes status and error body`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))
        val dataSource = HttpOverpassDataSource(maximumConnectTimeoutMillis = 1_000)

        val error = expectIOException {
            dataSource.fetch(
                endpoint = server.url("/interpreter").toString(),
                query = "query",
                timeoutMillis = 2_000,
            )
        }

        assertTrue(error.message.orEmpty().contains("503"))
        assertTrue(error.message.orEmpty().contains("temporarily unavailable"))
    }

    private fun expectIOException(block: () -> Unit): IOException = try {
        block()
        throw AssertionError("Expected IOException")
    } catch (error: IOException) {
        error
    }
}
