package com.safewalk.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.charset.StandardCharsets

class OverpassGraphParserTest {
    private val parser = OverpassGraphParser()
    private val emptySafetyData = RouteSafetyData(emptyList(), emptyList(), emptyList())

    @Test
    fun `way is converted to bidirectional route edges`() {
        val graph = parser.parse(VALID_OVERPASS_RESPONSE, emptySafetyData)

        assertEquals(setOf("1", "2"), graph.nodes.keys)
        assertEquals("2", graph.adjacency.getValue("1").single().toNodeId)
        assertEquals("1", graph.adjacency.getValue("2").single().toNodeId)
        assertTrue(graph.adjacency.getValue("1").single().distanceMeters > 0)
        assertEquals(0.5, graph.adjacency.getValue("1").single().safetyScore, 0.0001)
    }

    @Test
    fun `malformed response is exposed as an io failure`() {
        val error = expectIOException { parser.parse("not-json", emptySafetyData) }

        assertTrue(error.message.orEmpty().contains("Invalid Overpass response"))
    }

    @Test
    fun `bundled road network is parseable and contains connected roads`() {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("road_network.json")) {
            "road_network.json must be included in unit test resources"
        }
        val json = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

        val graph = parser.parse(json, emptySafetyData)

        assertTrue(graph.nodes.isNotEmpty())
        assertTrue(graph.adjacency.isNotEmpty())
    }

    private fun expectIOException(block: () -> Unit): IOException = try {
        block()
        throw AssertionError("Expected IOException")
    } catch (error: IOException) {
        error
    }
}
