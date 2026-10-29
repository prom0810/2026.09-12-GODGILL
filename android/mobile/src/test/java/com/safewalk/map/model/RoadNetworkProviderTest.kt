package com.safewalk.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RoadNetworkProviderTest {
    @Test
    fun `first valid mirror response is returned without reading the bundle`() {
        val requestedMirrors = mutableListOf<String>()
        var bundleReadCount = 0
        val provider = provider(
            mirrors = listOf("first", "second"),
            remoteDataSource = OverpassRemoteDataSource { endpoint, _, _ ->
                requestedMirrors += endpoint
                VALID_OVERPASS_RESPONSE
            },
            bundledDataSource = BundledRoadNetworkDataSource {
                bundleReadCount++
                VALID_OVERPASS_RESPONSE
            },
        )

        val graph = provider.buildGraph(emptyList(), emptyList(), emptyList())

        assertEquals(listOf("first"), requestedMirrors)
        assertEquals(0, bundleReadCount)
        assertEquals(setOf("1", "2"), graph.nodes.keys)
    }

    @Test
    fun `failed mirror is followed by the next mirror`() {
        val requestedMirrors = mutableListOf<String>()
        val provider = provider(
            mirrors = listOf("first", "second"),
            remoteDataSource = OverpassRemoteDataSource { endpoint, _, _ ->
                requestedMirrors += endpoint
                if (endpoint == "first") throw IOException("temporary failure")
                VALID_OVERPASS_RESPONSE
            },
        )

        val graph = provider.buildGraph(emptyList(), emptyList(), emptyList())

        assertEquals(listOf("first", "second"), requestedMirrors)
        assertEquals(2, graph.nodes.size)
    }

    @Test
    fun `bundled road network is used after every mirror fails`() {
        val requestedMirrors = mutableListOf<String>()
        var bundleReadCount = 0
        val provider = provider(
            mirrors = listOf("first", "second"),
            remoteDataSource = OverpassRemoteDataSource { endpoint, _, _ ->
                requestedMirrors += endpoint
                throw IOException("offline")
            },
            bundledDataSource = BundledRoadNetworkDataSource {
                bundleReadCount++
                VALID_OVERPASS_RESPONSE
            },
        )

        val graph = provider.buildGraph(emptyList(), emptyList(), emptyList())

        assertEquals(listOf("first", "second"), requestedMirrors)
        assertEquals(1, bundleReadCount)
        assertEquals(2, graph.nodes.size)
    }

    @Test
    fun `invalid mirror response can recover with bundled data`() {
        val provider = provider(
            mirrors = listOf("invalid"),
            remoteDataSource = OverpassRemoteDataSource { _, _, _ -> "not-json" },
            bundledDataSource = BundledRoadNetworkDataSource { VALID_OVERPASS_RESPONSE },
        )

        val graph = provider.buildGraph(emptyList(), emptyList(), emptyList())

        assertEquals(2, graph.nodes.size)
    }

    @Test
    fun `remaining budget below minimum skips another mirror and uses bundle`() {
        var nowMillis = 0L
        val requestedMirrors = mutableListOf<String>()
        val timeouts = mutableListOf<Int>()
        val provider = provider(
            mirrors = listOf("first", "second"),
            remoteDataSource = OverpassRemoteDataSource { endpoint, _, timeoutMillis ->
                requestedMirrors += endpoint
                timeouts += timeoutMillis
                nowMillis = 9_000L
                throw IOException("slow mirror")
            },
            bundledDataSource = BundledRoadNetworkDataSource { VALID_OVERPASS_RESPONSE },
            clock = MonotonicClock { nowMillis },
            serverBudgetMillis = 10_000L,
            minimumAttemptMillis = 1_500L,
        )

        provider.buildGraph(emptyList(), emptyList(), emptyList())

        assertEquals(listOf("first"), requestedMirrors)
        assertEquals(listOf(10_000), timeouts)
    }

    @Test
    fun `missing remote and bundled road data reports a failure`() {
        val provider = provider(
            mirrors = listOf("offline"),
            remoteDataSource = OverpassRemoteDataSource { _, _, _ ->
                throw IOException("offline")
            },
            bundledDataSource = BundledRoadNetworkDataSource { """{"elements": []}""" },
        )

        val error = expectIOException {
            provider.buildGraph(emptyList(), emptyList(), emptyList())
        }

        assertTrue(error.message.orEmpty().contains("bundled road network"))
    }

    private fun provider(
        mirrors: List<String>,
        remoteDataSource: OverpassRemoteDataSource,
        bundledDataSource: BundledRoadNetworkDataSource? = null,
        clock: MonotonicClock = MonotonicClock { 0L },
        serverBudgetMillis: Long = 10_000L,
        minimumAttemptMillis: Long = 1_500L,
    ) = RoadNetworkProvider(
        mirrors = mirrors,
        remoteDataSource = remoteDataSource,
        bundledDataSource = bundledDataSource,
        clock = clock,
        serverBudgetMillis = serverBudgetMillis,
        minimumAttemptMillis = minimumAttemptMillis,
    )

    private fun expectIOException(block: () -> Unit): IOException = try {
        block()
        throw AssertionError("Expected IOException")
    } catch (error: IOException) {
        error
    }
}
