package com.safewalk.map.model

import android.content.res.AssetManager
import com.safewalk.map.viewmodel.MapViewport

interface MapRepository {
    val canLoadSafeMap: Boolean
    val canSearchPlaces: Boolean

    fun loadSafeMap(viewport: MapViewport): android.graphics.Bitmap
    fun loadFacilities(bounds: WmsBounds): List<Facility>
    fun loadCctvSites(): List<CctvSite>
    fun loadSecurityLightSites(): List<SecurityLightSite>
    fun searchPlaces(query: String): List<Place>
    fun buildRouteGraph(
        cctvSites: List<CctvSite>,
        securityLightSites: List<SecurityLightSite>,
        facilities: List<Facility>,
    ): RouteGraph
}

class DefaultMapRepository(
    safeMapServiceKey: String,
    kakaoRestApiKey: String,
    private val assets: AssetManager,
    private val routeGraphProvider: RouteGraphProvider = RoadNetworkProvider(
        bundledDataSource = AssetRoadNetworkDataSource(assets),
    ),
) : MapRepository {
    private val safeMapClient: SafeMapDataSource? =
        safeMapServiceKey.takeIf(String::isNotBlank)?.let(::SafeMapWmsClient)
    private val facilitySearch = kakaoRestApiKey.takeIf(String::isNotBlank)?.let(::NearbyFacilitySearch)
    private val placeSearch = kakaoRestApiKey.takeIf(String::isNotBlank)?.let(::KakaoPlaceSearch)
    private val cctvRepository = CheonanCctvRepository()
    private val securityLightRepository = SecurityLightRepository(assets)

    override val canLoadSafeMap: Boolean = safeMapClient != null
    override val canSearchPlaces: Boolean = placeSearch != null

    override fun loadSafeMap(viewport: MapViewport) = requireNotNull(safeMapClient) {
        "SafeMap service key is missing"
    }.load(viewport.bounds, viewport.width, viewport.height)

    override fun loadFacilities(bounds: WmsBounds): List<Facility> =
        facilitySearch?.search(bounds).orEmpty()

    override fun loadCctvSites(): List<CctvSite> = cctvRepository.loadNearCheonanStation()

    override fun loadSecurityLightSites(): List<SecurityLightSite> =
        securityLightRepository.loadNearCheonanStation()

    override fun searchPlaces(query: String): List<Place> = requireNotNull(placeSearch) {
        "Kakao REST API key is missing"
    }.search(query)

    override fun buildRouteGraph(
        cctvSites: List<CctvSite>,
        securityLightSites: List<SecurityLightSite>,
        facilities: List<Facility>,
    ): RouteGraph = routeGraphProvider.buildGraph(cctvSites, securityLightSites, facilities)
}
