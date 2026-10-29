package com.safewalk.map.model

import java.io.IOException

/**
 * 제한 시간 안에 Overpass 미러를 순서대로 호출하고, 모두 실패하면 번들 도로망을 사용한다.
 * HTTP, 파일 읽기, JSON 파싱은 별도 객체에 위임해 각 동작을 독립적으로 검증할 수 있다.
 */
internal class RoadNetworkProvider(
    private val mirrors: List<String> = DEFAULT_MIRRORS,
    private val remoteDataSource: OverpassRemoteDataSource = HttpOverpassDataSource(),
    private val bundledDataSource: BundledRoadNetworkDataSource? = null,
    private val graphParser: OverpassGraphParser = OverpassGraphParser(),
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val serverBudgetMillis: Long = SERVER_BUDGET_MILLIS,
    private val minimumAttemptMillis: Long = MINIMUM_ATTEMPT_MILLIS,
) : RouteGraphProvider {
    init {
        require(serverBudgetMillis > 0) { "Server budget must be positive" }
        require(minimumAttemptMillis > 0) { "Minimum attempt time must be positive" }
    }

    @Throws(IOException::class)
    override fun buildGraph(
        cctvSites: List<CctvSite>,
        securityLightSites: List<SecurityLightSite>,
        facilities: List<Facility>,
    ): RouteGraph {
        val safetyData = RouteSafetyData(cctvSites, securityLightSites, facilities)
        val query = buildOverpassQuery()
        val startedAt = clock.nowMillis()
        var lastError: IOException? = null

        for (mirror in mirrors) {
            val elapsedMillis = (clock.nowMillis() - startedAt).coerceAtLeast(0)
            val remainingMillis = serverBudgetMillis - elapsedMillis
            if (remainingMillis < minimumAttemptMillis) break

            try {
                val response = remoteDataSource.fetch(
                    endpoint = mirror,
                    query = query,
                    timeoutMillis = remainingMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
                return parseUsableGraph(response, safetyData, "mirror=$mirror")
            } catch (error: IOException) {
                lastError = error
            }
        }

        bundledDataSource?.let { source ->
            try {
                return parseUsableGraph(source.read(), safetyData, "bundled road network")
            } catch (error: IOException) {
                lastError = error
            }
        }

        throw lastError ?: IOException("Unable to load road network data")
    }

    @Throws(IOException::class)
    private fun parseUsableGraph(
        json: String,
        safetyData: RouteSafetyData,
        sourceDescription: String,
    ): RouteGraph {
        val graph = graphParser.parse(json, safetyData)
        if (graph.nodes.isEmpty() || graph.adjacency.isEmpty()) {
            throw IOException("No usable road data in $sourceDescription")
        }
        return graph
    }

    private fun buildOverpassQuery(): String =
        """
            [out:json][timeout:25];
            (
              way["highway"~"^(footway|path|pedestrian|living_street|residential|steps|track|service|tertiary|secondary|primary|unclassified)$"]
                (around:$RADIUS_METERS,${RouteSafetyScorer.CENTER_LATITUDE},${RouteSafetyScorer.CENTER_LONGITUDE});
            );
            (._;>;);
            out body;
        """.trimIndent()

    companion object {
        private val DEFAULT_MIRRORS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.openstreetmap.ru/api/interpreter",
        )
        private const val SERVER_BUDGET_MILLIS = 10_000L
        private const val MINIMUM_ATTEMPT_MILLIS = 1_500L
        private const val RADIUS_METERS = 1_500.0
    }
}

internal data class RouteSafetyData(
    val cctvSites: List<CctvSite>,
    val securityLightSites: List<SecurityLightSite>,
    val facilities: List<Facility>,
)
