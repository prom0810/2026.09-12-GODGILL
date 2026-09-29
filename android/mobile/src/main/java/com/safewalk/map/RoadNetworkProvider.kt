package com.safewalk.map

import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Overpass API로 실제 보행자 도로망(OSM)을 받아와 [RouteGraph]로 변환한다.
 * 공용 미러 서버가 불안정할 수 있어 여러 서버를 순차 재시도(failover)한다.
 *
 * 주의: 블로킹 네트워크 호출이다. 반드시 백그라운드 Executor에서 호출해야 하며,
 * MapActivity의 다른 저장소들(CheonanCctvRepository 등)과 동일한 스타일(코루틴 대신
 * 블로킹 IO + Executor)을 따른다.
 */
object RoadNetworkProvider {

    private val OVERPASS_MIRRORS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.openstreetmap.ru/api/interpreter",
    )

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val RADIUS_M = 1500.0

    @Throws(IOException::class)
    fun buildGraph(
        cctvSites: List<CctvSite>,
        securityLightSites: List<SecurityLightSite>,
        facilities: List<Facility>,
    ): RouteGraph {
        val query = buildOverpassQuery()

        var lastError: IOException? = null
        for (mirror in OVERPASS_MIRRORS) {
            try {
                val json = fetchOverpass(mirror, query)
                val graph = parseToGraph(json, cctvSites, securityLightSites, facilities)
                if (graph.nodes.isEmpty() || graph.adjacency.isEmpty()) {
                    throw IOException("Overpass 응답에 유효한 도로 데이터가 없음 (mirror=$mirror)")
                }
                return graph
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("모든 Overpass 미러 서버 요청 실패")
    }

    private fun buildOverpassQuery(): String {
        return """
            [out:json][timeout:25];
            (
              way["highway"~"^(footway|path|pedestrian|living_street|residential|steps|track|service|tertiary|secondary|primary|unclassified)$"]
                (around:$RADIUS_M,${RouteSafetyScorer.CENTER_LATITUDE},${RouteSafetyScorer.CENTER_LONGITUDE});
            );
            (._;>;);
            out body;
        """.trimIndent()
    }

    private fun fetchOverpass(endpoint: String, query: String): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            // 406 Not Acceptable 방지: Accept/User-Agent 헤더 없는 요청을 거부하는 서버가 있음
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("User-Agent", "Safewalk-Android/1.0")

            connection.outputStream.use { output ->
                output.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray(StandardCharsets.UTF_8))
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.let { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
                } ?: ""
                throw IOException("Overpass API 오류(${connection.responseCode}): $errorBody")
            }

            return BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseToGraph(
        json: String,
        cctvSites: List<CctvSite>,
        securityLightSites: List<SecurityLightSite>,
        facilities: List<Facility>,
    ): RouteGraph {
        val root = JSONObject(json)
        val elements = root.getJSONArray("elements")

        val nodes = LinkedHashMap<String, RouteNode>()
        val ways = mutableListOf<List<String>>()

        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            when (el.getString("type")) {
                "node" -> {
                    val id = el.getLong("id").toString()
                    nodes[id] = RouteNode(id, el.getDouble("lat"), el.getDouble("lon"))
                }
                "way" -> {
                    val nds = el.getJSONArray("nodes")
                    ways += List(nds.length()) { nds.getLong(it).toString() }
                }
            }
        }

        // 1차: raw 안전도 점수만 모아둔다 (아직 정규화 안 함)
        data class RawEdge(val fromId: String, val toId: String, val distance: Double, val raw: Double)
        val rawEdges = mutableListOf<RawEdge>()

        fun collectEdge(fromId: String, toId: String) {
            val from = nodes[fromId] ?: return
            val to = nodes[toId] ?: return
            val distance = RouteSafetyScorer.haversineMeters(from.lat, from.lon, to.lat, to.lon)
            if (distance <= 0.5) return
            val midLat = (from.lat + to.lat) / 2
            val midLon = (from.lon + to.lon) / 2
            val raw = RouteSafetyScorer.rawSafetyScore(midLat, midLon, cctvSites, securityLightSites, facilities)
            rawEdges += RawEdge(fromId, toId, distance, raw)
        }

        for (way in ways) {
            for (i in 0 until way.size - 1) {
                collectEdge(way[i], way[i + 1])
                collectEdge(way[i + 1], way[i]) // 보행자 도로는 양방향 통행 가정
            }
        }

        // 2차: 이 지역 도로망에서 실제로 관찰된 min~max로 정규화
        val minRaw = rawEdges.minOfOrNull { it.raw } ?: 0.0
        val maxRaw = rawEdges.maxOfOrNull { it.raw } ?: 1.0

        val adjacency = HashMap<String, MutableList<RouteEdge>>()
        for (re in rawEdges) {
            val normalized = RouteSafetyScorer.normalize(re.raw, minRaw, maxRaw)
            adjacency.getOrPut(re.fromId) { mutableListOf() } += RouteEdge(re.toId, re.distance, normalized)
        }

        val connectedIds = adjacency.keys + adjacency.values.flatten().map { it.toNodeId }
        return RouteGraph(nodes.filterKeys { it in connectedIds }, adjacency)
    }
}
