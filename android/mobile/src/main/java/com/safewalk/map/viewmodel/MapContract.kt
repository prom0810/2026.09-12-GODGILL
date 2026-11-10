package com.safewalk.map.viewmodel

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.safewalk.map.model.CctvSite
import com.safewalk.map.model.Facility
import com.safewalk.map.model.Place
import com.safewalk.map.model.RouteAStar
import com.safewalk.map.model.SearchHistoryItem
import com.safewalk.map.model.SecurityLightSite
import com.safewalk.map.model.WmsBounds

data class MapViewport(
    val bounds: WmsBounds,
    val width: Int,
    val height: Int,
)

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 서버에 저장된 진행 중 경로 안내 (POST /api/routes 성공 후 ~ 도착/취소 전).
 * 앱을 다시 켜면 GET /api/routes/active로 복원된다 (이때는 경로 선을 다시 그리지 않는다).
 */
data class GuidanceState(
    val routeId: Long,
    val startName: String,
    val endName: String,
)

data class RouteUiState(
    val startPoint: GeoPoint? = null,
    val endPoint: GeoPoint? = null,
    val startName: String = "",
    val endName: String = "",
    val shortestResult: RouteAStar.RouteResult? = null,
    val safeResult: RouteAStar.RouteResult? = null,
    val displayedIsSafe: Boolean = false,
    val isLoading: Boolean = false,
    val guidance: GuidanceState? = null,
    /** 안내 시작·종료 요청이 서버로 가는 중 (버튼 중복 클릭 방지) */
    val guidanceBusy: Boolean = false,
) {
    val displayedResult: RouteAStar.RouteResult?
        get() = if (displayedIsSafe) safeResult else shortestResult
}

data class MapUiState(
    val isMapReady: Boolean = false,
    val facilitiesEnabled: Boolean = false,
    val cctvEnabled: Boolean = false,
    val securityLightsEnabled: Boolean = false,
    val safeMapEnabled: Boolean = false,
    val facilities: List<Facility> = emptyList(),
    val cctvSites: List<CctvSite> = emptyList(),
    val securityLightSites: List<SecurityLightSite> = emptyList(),
    val searchResults: List<Place> = emptyList(),
    val isSearching: Boolean = false,
    val route: RouteUiState = RouteUiState(),
    val history: List<SearchHistoryItem> = emptyList(),
)

data class UiText(
    @param:StringRes val resourceId: Int,
    val arguments: List<Any> = emptyList(),
)

sealed interface MapEffect {
    data class SafeMapImageReady(val bitmap: Bitmap) : MapEffect
    data class MoveCamera(val point: GeoPoint) : MapEffect
    data class ChooseRoutePoint(val isStart: Boolean, val places: List<Place>) : MapEffect
    /** 입력창 텍스트를 채운다. null이면 그 입력창은 그대로 둔다. */
    data class FillRouteQueries(val startName: String?, val endName: String?) : MapEffect
    data class ShowMessage(val text: UiText) : MapEffect
}
