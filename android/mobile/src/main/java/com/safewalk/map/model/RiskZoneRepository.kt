package com.safewalk.map.model

import android.content.Context
import com.safewalk.common.network.ApiClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * 위험구역 1개 (서버 risk_area). 중심 좌표 + 반경(m)의 원.
 *
 * @param severity 경로 구간 감점 최대값 (risk_area.risk_score). 중심에서 최대, 경계에서 0
 * @param label 알림 문구에 쓰는 이름 (설명이 있으면 설명, 없으면 유형 라벨)
 */
data class RiskZone(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val radiusM: Double,
    val severity: Double,
    val label: String,
)

/**
 * 위험구역 목록. 서버 GET /api/risk-areas(토큰 불필요)에서 받아 SharedPreferences에 캐시한다.
 *
 *  - [loadCached]: 앱 시작 시 캐시를 [RouteSafetyScorer.riskZones]에 넣는다 (캐시가 없으면 목업 그대로)
 *  - [refresh]: 서버에서 다시 받아 캐시 + 반영 (IO 스레드). 실패하면 기존 값 유지
 *
 * 경로 안전도 감점(RouteSafetyScorer)과 안내 중 접근·진입 알림(RiskZoneMonitor)이 같은 목록을 쓴다.
 */
object RiskZoneRepository {
    private const val PREFS = "safewalk_risk_zones"
    private const val KEY_JSON = "zones"
    private const val KEY_FETCHED_AT = "fetchedAt"
    /** 이 시간 안에 받은 적이 있으면 refresh(force=false)를 건너뛴다 */
    private const val REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun loadCached(context: Context) {
        val json = prefs(context).getString(KEY_JSON, null) ?: return
        runCatching { parse(JSONArray(json)) }.getOrNull()?.let { RouteSafetyScorer.riskZones = it }
    }

    fun refresh(context: Context, force: Boolean = false) {
        val p = prefs(context)
        if (!force && System.currentTimeMillis() - p.getLong(KEY_FETCHED_AT, 0L) < REFRESH_INTERVAL_MS &&
            p.contains(KEY_JSON)
        ) return
        val array = runCatching { ApiClient.get("/api/risk-areas") as? JSONArray }.getOrNull() ?: return
        val zones = runCatching { parse(array) }.getOrNull() ?: return
        p.edit().putString(KEY_JSON, array.toString()).putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply()
        RouteSafetyScorer.riskZones = zones
    }

    /** 서버 응답 배열 → 구역 목록. 잘못된 항목(반경 0 이하 등)은 건너뛴다 */
    fun parse(array: JSONArray): List<RiskZone> =
        (0 until array.length()).mapNotNull { i ->
            val o: JSONObject = array.optJSONObject(i) ?: return@mapNotNull null
            val radius = o.optDouble("radius")
            if (!o.has("lat") || !o.has("lng") || radius.isNaN() || radius <= 0.0) return@mapNotNull null
            val description = if (o.isNull("description")) "" else o.optString("description").trim()
            RiskZone(
                id = o.optLong("riskAreaId"),
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                radiusM = radius,
                severity = o.optDouble("riskScore", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                label = description.ifBlank { o.optString("riskTypeLabel").ifBlank { "위험구역" } },
            )
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
