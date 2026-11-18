package com.safewalk.watch

import android.content.Context
import org.json.JSONArray

/**
 * 지금 경로 안내 중인지 (routeId). 워치 이상 감지 팝업은 안내 중에만 띄운다 ([SensorAlertCoordinator]).
 * 앱이 꺼져 있어도 WatchMessageService가 확인할 수 있게 SharedPreferences에 둔다.
 * 안내 시작·복원 시 [set], 도착·취소·로그아웃 시 [clear] (MapViewModel, MyActivity).
 *
 * 경로 이탈 감지용으로 안내 경로 좌표([setPath])와, 안내가 위치 공유를 자동으로 켰는지([setAutoSharing])도 둔다.
 */
object GuidanceStore {
    private const val PREFS = "safewalk_guidance"
    private const val KEY_ROUTE_ID = "routeId"
    private const val KEY_PATH_ROUTE_ID = "pathRouteId"
    private const val KEY_PATH = "path"
    private const val KEY_AUTO_SHARING = "autoSharing"

    fun activeRouteId(context: Context): Long? =
        prefs(context).getLong(KEY_ROUTE_ID, 0L).takeIf { it > 0L }

    fun set(context: Context, routeId: Long) {
        prefs(context).edit().putLong(KEY_ROUTE_ID, routeId).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_ROUTE_ID).remove(KEY_PATH_ROUTE_ID).remove(KEY_PATH).remove(KEY_AUTO_SHARING)
            .apply()
    }

    /** 안내 경로 좌표 [[lat, lng], ...] (출발 → 도착 순서) */
    fun setPath(context: Context, routeId: Long, path: List<DoubleArray>) {
        val array = JSONArray()
        path.forEach { array.put(JSONArray().put(it[0]).put(it[1])) }
        prefs(context).edit().putLong(KEY_PATH_ROUTE_ID, routeId).putString(KEY_PATH, array.toString()).apply()
    }

    /** routeId의 경로 좌표. 저장된 것이 다른 경로거나 없으면 null */
    fun path(context: Context, routeId: Long): List<DoubleArray>? {
        val p = prefs(context)
        if (p.getLong(KEY_PATH_ROUTE_ID, 0L) != routeId) return null
        val json = p.getString(KEY_PATH, null) ?: return null
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val point = array.getJSONArray(i)
                doubleArrayOf(point.getDouble(0), point.getDouble(1))
            }
        }.getOrNull()?.takeIf { it.size >= 2 }
    }

    /** 안내 시작 때 위치 공유를 자동으로 켰는지. true면 안내가 끝날 때 공유도 끈다 (사용자가 미리 켜 둔 공유는 유지) */
    fun setAutoSharing(context: Context, auto: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SHARING, auto).apply()
    }

    fun isAutoSharing(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_SHARING, false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
