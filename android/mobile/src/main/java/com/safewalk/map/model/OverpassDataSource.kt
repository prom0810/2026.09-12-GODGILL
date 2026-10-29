package com.safewalk.map.model

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun interface OverpassRemoteDataSource {
    @Throws(IOException::class)
    fun fetch(endpoint: String, query: String, timeoutMillis: Int): String
}

internal class HttpOverpassDataSource(
    private val maximumConnectTimeoutMillis: Int = 8_000,
) : OverpassRemoteDataSource {
    @Throws(IOException::class)
    override fun fetch(endpoint: String, query: String, timeoutMillis: Int): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = minOf(maximumConnectTimeoutMillis, timeoutMillis)
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8",
            )
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Safewalk-Android/1.0")

            val requestBody = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.let { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use {
                        it.readText()
                    }
                }.orEmpty()
                val detail = errorBody.takeIf(String::isNotBlank)
                    ?.let { ": ${it.take(MAX_ERROR_BODY_LENGTH)}" }
                    .orEmpty()
                throw IOException("Overpass API request failed ($responseCode)$detail")
            }

            return BufferedReader(
                InputStreamReader(connection.inputStream, StandardCharsets.UTF_8),
            ).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_ERROR_BODY_LENGTH = 512
    }
}

internal fun interface MonotonicClock {
    fun nowMillis(): Long
}

internal object SystemMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long = System.nanoTime() / 1_000_000L
}
