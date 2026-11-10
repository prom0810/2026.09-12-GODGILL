package com.safewalk.map.model

import android.content.Context
import com.safewalk.common.network.AuthenticatedApiClient
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** 서버에 저장된 진행 중 경로 */
data class ActiveRoute(
    val routeId: Long,
    val startName: String?,
    val endName: String?,
)

/** POST /api/routes 결과 */
data class StartedRoute(
    val routeId: Long,
    /** 진행 중이던 경로가 있어서 서버가 자동으로 취소했다면 그 ID */
    val cancelledRouteId: Long?,
)

/**
 * 경로 안내 기록 API (백엔드 /api/routes).
 * 경로 계산은 앱이 하고, 사용자가 "안내 시작"을 누른 경로만 서버에 저장한다.
 * 모든 호출은 네트워크를 쓰므로 IO 스레드에서 부른다.
 */
class RouteGuidanceRepository(context: Context) {
    private val api = AuthenticatedApiClient(context)

    fun start(result: RouteAStar.RouteResult, startName: String, endName: String): StartedRoute {
        val data = api.postObject("/api/routes", buildRouteStartRequest(result, startName, endName))
        return StartedRoute(
            routeId = data.getLong("routeId"),
            cancelledRouteId = data.optLong("cancelledRouteId").takeIf { !data.isNull("cancelledRouteId") && it > 0L },
        )
    }

    /** completed=true면 도착(COMPLETED), false면 안내 취소(CANCELLED) */
    fun finish(routeId: Long, completed: Boolean) {
        api.patchObject(
            "/api/routes/$routeId",
            JSONObject().put("status", if (completed) "COMPLETED" else "CANCELLED"),
        )
    }

    /** 진행 중 경로. 없으면 null (서버 data: null) */
    fun active(): ActiveRoute? {
        val data = api.getObject("/api/routes/active")
        if (data.length() == 0 || !data.has("routeId")) return null
        return ActiveRoute(
            routeId = data.getLong("routeId"),
            startName = data.optJSONObject("start")?.optStringOrNull("name"),
            endName = data.optJSONObject("end")?.optStringOrNull("name"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

/** 화면 요약과 같은 보행 속도 (분당 67m) */
private const val WALKING_METERS_PER_MINUTE = 67.0

/** 서버 지점 최대 개수 (RouteDraft.MAX_POINTS) */
const val MAX_ROUTE_POINTS = 500

/**
 * A* 결과를 POST /api/routes 요청 본문으로 만든다.
 * 지점 = 첫 구간의 출발 노드 + 각 구간의 도착 노드, 구간 i = 지점 i → i+1.
 * 거리는 서버가 좌표로 다시 계산하므로 보내지 않는다.
 */
fun buildRouteStartRequest(result: RouteAStar.RouteResult, startName: String, endName: String): JSONObject {
    require(result.segments.isNotEmpty()) { "경로 구간이 없습니다." }
    require(result.segments.size < MAX_ROUTE_POINTS) { "경로 지점이 너무 많습니다." }

    val points = JSONArray()
    val nodes = listOf(result.segments.first().from) + result.segments.map { it.to }
    nodes.forEachIndexed { index, node ->
        val point = JSONObject().put("lat", node.lat).put("lng", node.lon)
        when (index) {
            0 -> startName.takeIf { it.isNotBlank() }?.let { point.put("name", it) }
            nodes.lastIndex -> endName.takeIf { it.isNotBlank() }?.let { point.put("name", it) }
        }
        points.put(point)
    }

    val segments = JSONArray()
    result.segments.forEach { segment ->
        val facilities = JSONArray()
        segment.facilities.forEach { facility ->
            facilities.put(
                JSONObject()
                    .put("type", facility.type)
                    .put("lat", facility.lat)
                    .put("lng", facility.lon)
                    .put("quantity", facility.quantity)
                    .put("distance", facility.distanceMeters)
                    .put("appliedScore", facility.appliedScore),
            )
        }
        segments.put(
            JSONObject()
                .put("safetyScore", segment.safetyScore.coerceIn(0.0, 1.0))
                .put("riskScore", segment.riskPenalty.coerceAtLeast(0.0))
                .put("facilities", facilities),
        )
    }

    val estimatedSeconds = (result.totalDistanceMeters / WALKING_METERS_PER_MINUTE * 60).roundToInt()
    return JSONObject()
        .put("estimatedTime", estimatedSeconds.coerceAtLeast(0))
        .put("safetyScore", result.averageSafetyScore.coerceIn(0.0, 1.0))
        .put("points", points)
        .put("segments", segments)
}
