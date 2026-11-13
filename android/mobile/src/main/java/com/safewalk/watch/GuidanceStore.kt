package com.safewalk.watch

import android.content.Context

/**
 * 지금 경로 안내 중인지 (routeId). 워치 이상 감지 팝업은 안내 중에만 띄운다 ([SensorAlertCoordinator]).
 * 앱이 꺼져 있어도 WatchMessageService가 확인할 수 있게 SharedPreferences에 둔다.
 * 안내 시작·복원 시 [set], 도착·취소·로그아웃 시 [clear] (MapViewModel, MyActivity).
 */
object GuidanceStore {
    private const val PREFS = "safewalk_guidance"
    private const val KEY_ROUTE_ID = "routeId"

    fun activeRouteId(context: Context): Long? =
        prefs(context).getLong(KEY_ROUTE_ID, 0L).takeIf { it > 0L }

    fun set(context: Context, routeId: Long) {
        prefs(context).edit().putLong(KEY_ROUTE_ID, routeId).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ROUTE_ID).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
