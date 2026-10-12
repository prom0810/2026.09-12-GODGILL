package com.safewalk.map.viewmodel

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.safewalk.map.model.CctvSite
import com.safewalk.map.model.Facility
import com.safewalk.map.model.Place
import com.safewalk.map.model.RouteAStar
import com.safewalk.map.model.SecurityLightSite
import com.safewalk.map.model.WmsBounds

data class MapViewport(
    val bounds: WmsBounds,
    val width: Int,
    val height: Int,
)

data class GeoPoint(val latitude: Double, val longitude: Double)

data class RouteUiState(
    val startPoint: GeoPoint? = null,
    val endPoint: GeoPoint? = null,
    val shortestResult: RouteAStar.RouteResult? = null,
    val safeResult: RouteAStar.RouteResult? = null,
    val displayedIsSafe: Boolean = false,
    val isLoading: Boolean = false,
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
)

data class UiText(
    @param:StringRes val resourceId: Int,
    val arguments: List<Any> = emptyList(),
)

sealed interface MapEffect {
    data class SafeMapImageReady(val bitmap: Bitmap) : MapEffect
    data class MoveCamera(val point: GeoPoint) : MapEffect
    data class ShowMessage(val text: UiText) : MapEffect
}
