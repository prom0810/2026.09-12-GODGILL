package com.safewalk.map.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

enum class HistoryKind { PLACE, ROUTE }

/**
 * 검색 기록 1건.
 * - PLACE: 출발지/도착지로 고른 장소 하나 (name, address, latitude, longitude)
 * - ROUTE: 경로 계산에 성공한 출발지→도착지 (name/lat/lng = 출발지, endName/endLat/endLng = 도착지)
 */
data class SearchHistoryItem(
    val kind: HistoryKind,
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val endName: String = "",
    val endLatitude: Double = 0.0,
    val endLongitude: Double = 0.0,
    val savedAt: Long = 0L,
) {
    /** 같은 장소/같은 경로(좌표 약 1m 단위)는 같은 기록으로 본다. */
    val key: String
        get() = when (kind) {
            HistoryKind.PLACE -> "P:${q(latitude)},${q(longitude)}"
            HistoryKind.ROUTE -> "R:${q(latitude)},${q(longitude)}>${q(endLatitude)},${q(endLongitude)}"
        }

    fun toPlace() = Place(name, address, latitude, longitude)

    private fun q(v: Double) = (v * 100_000).roundToLong()

    companion object {
        fun place(place: Place, now: Long = System.currentTimeMillis()) = SearchHistoryItem(
            kind = HistoryKind.PLACE,
            name = place.name,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
            savedAt = now,
        )

        fun route(
            startName: String, startLat: Double, startLng: Double,
            endName: String, endLat: Double, endLng: Double,
            now: Long = System.currentTimeMillis(),
        ) = SearchHistoryItem(
            kind = HistoryKind.ROUTE,
            name = startName, latitude = startLat, longitude = startLng,
            endName = endName, endLatitude = endLat, endLongitude = endLng,
            savedAt = now,
        )
    }
}

/**
 * 출발지/도착지 검색 기록을 기기 안(SharedPreferences)에만 저장한다. 서버로 보내지 않는다.
 * 최신순 최대 [MAX_ITEMS]건, 같은 항목을 다시 검색하면 맨 위로 올린다.
 */
class SearchHistoryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<SearchHistoryItem> = try {
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                SearchHistoryItem(
                    kind = HistoryKind.valueOf(o.getString("k")),
                    name = o.getString("n"),
                    address = o.optString("a"),
                    latitude = o.getDouble("la"),
                    longitude = o.getDouble("lo"),
                    endName = o.optString("en"),
                    endLatitude = o.optDouble("ea", 0.0),
                    endLongitude = o.optDouble("eo", 0.0),
                    savedAt = o.optLong("t"),
                )
            }.getOrNull() // 깨진 항목 하나만 건너뛴다
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun add(item: SearchHistoryItem): List<SearchHistoryItem> {
        val next = (listOf(item) + load().filterNot { it.key == item.key }).take(MAX_ITEMS)
        save(next)
        return next
    }

    fun remove(item: SearchHistoryItem): List<SearchHistoryItem> {
        val next = load().filterNot { it.key == item.key }
        save(next)
        return next
    }

    fun clear() {
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    private fun save(items: List<SearchHistoryItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject()
                .put("k", it.kind.name).put("n", it.name).put("a", it.address)
                .put("la", it.latitude).put("lo", it.longitude)
                .put("en", it.endName).put("ea", it.endLatitude).put("eo", it.endLongitude)
                .put("t", it.savedAt))
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        const val MAX_ITEMS = 30
        private const val PREFS_NAME = "search_history"
        private const val KEY_ITEMS = "items"
    }
}
