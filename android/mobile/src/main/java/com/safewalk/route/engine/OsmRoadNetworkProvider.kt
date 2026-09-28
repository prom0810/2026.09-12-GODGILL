package com.safewalk.route.engine

import com.safewalk.route.model.Edge
import com.safewalk.route.model.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * OpenStreetMap(Overpass API)에서 실제 보행자 도로망을 받아 A* 그래프로 변환한다.
 * SafetyDataProvider의 150m 격자 목업 대신 이 그래프를 쓰면, 경로가 실제 도로를 따라 그려진다.
 *
 * 별도 API 키가 필요 없는 공개 API(Overpass)를 사용한다.
 * 안전도 점수는 SafetyDataProvider의 위험구역 목업 로직을 그대로 재사용해,
 * "격자 그래프 vs 실도로 그래프"만 바뀌고 안전도 계산 기준은 동일하게 유지한다.
 */
object OsmRoadNetworkProvider {

    // 공개 Overpass 서버는 종종 느리거나 막히므로, 하나가 실패하면 다음 미러로 자동 재시도한다.
    private val OVERPASS_URLS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter"
    )
    private const val RADIUS_M = 1500

    // 보행 가능한 도로 유형만 대상으로 한다 (자동차 전용도로 등 제외)
    private const val HIGHWAY_FILTER =
        "^(footway|path|pedestrian|living_street|residential|service|tertiary|unclassified|secondary|primary|steps)$"

    suspend fun buildGraph(centerLat: Double, centerLon: Double): SafetyDataProvider.Graph =
        withContext(Dispatchers.IO) {
            val query = """
                [out:json][timeout:40];
                (
                  way["highway"~"$HIGHWAY_FILTER"](around:$RADIUS_M,$centerLat,$centerLon);
                );
                out body;
                >;
                out skel qt;
            """.trimIndent()

            var lastError: Exception? = null
            for (url in OVERPASS_URLS) {
                try {
                    val responseBody = fetchOverpass(url, query)
                    return@withContext parseToGraph(responseBody)
                } catch (e: Exception) {
                    lastError = e // 이 서버는 실패, 다음 미러로 시도
                }
            }
            throw lastError ?: IOException("Overpass 서버에 연결할 수 없습니다.")
        }

    private fun fetchOverpass(url: String, query: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 40000 // 도심 밀집지역은 응답 생성에 시간이 걸릴 수 있어 넉넉하게 설정
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }

        try {
            val payload = "data=" + URLEncoder.encode(query, "UTF-8")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream.bufferedReader().use { it.readText() }

            if (responseCode !in 200..299) {
                throw IOException("Overpass API 오류($responseCode): $text")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun parseToGraph(json: String): SafetyDataProvider.Graph {
        val root = JSONObject(json)
        val elements = root.getJSONArray("elements")

        val nodes = LinkedHashMap<String, Node>()
        val ways = mutableListOf<List<String>>()

        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            when (el.getString("type")) {
                "node" -> {
                    val id = el.getLong("id").toString()
                    val lat = el.getDouble("lat")
                    val lon = el.getDouble("lon")
                    nodes[id] = Node(id, lat, lon)
                }
                "way" -> {
                    val nds = el.getJSONArray("nodes")
                    val idList = mutableListOf<String>()
                    for (j in 0 until nds.length()) {
                        idList.add(nds.getLong(j).toString())
                    }
                    ways.add(idList)
                }
            }
        }

        val adjacency = HashMap<String, MutableList<Edge>>()

        fun addEdge(fromId: String, toId: String) {
            val from = nodes[fromId] ?: return
            val to = nodes[toId] ?: return
            val distance = SafetyDataProvider.haversineMeters(from.lat, from.lon, to.lat, to.lon)
            if (distance <= 0.5) return // 중복/제로 길이 구간 제외
            val safety = SafetyDataProvider.safetyScoreForSegment(from, to)
            adjacency.getOrPut(fromId) { mutableListOf() }.add(Edge(toId, distance, safety))
        }

        for (way in ways) {
            for (i in 0 until way.size - 1) {
                addEdge(way[i], way[i + 1])
                addEdge(way[i + 1], way[i]) // 보행자 도로는 양방향 통행 가정
            }
        }

        // 어느 도로에도 연결되지 않은 고립 노드는 제거해 탐색 대상을 줄인다
        val connectedIds = adjacency.keys + adjacency.values.flatten().map { it.toNodeId }
        val filteredNodes = nodes.filterKeys { it in connectedIds }

        return SafetyDataProvider.Graph(filteredNodes, adjacency)
    }
}
