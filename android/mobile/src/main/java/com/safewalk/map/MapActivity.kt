package com.safewalk.map

import android.app.Activity
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import android.widget.EditText
import android.widget.Button
import android.widget.ListView
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.CheckBox
import android.widget.ArrayAdapter
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.kakao.vectormap.camera.CameraUpdateFactory
import java.util.concurrent.Executors
import java.util.concurrent.Future
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.GestureType
import com.safewalk.BuildConfig
import com.safewalk.R

class MapActivity : Activity() {
    private var mapView: MapView? = null
    private lateinit var status: TextView
    private var kakaoMap: KakaoMap? = null
    private val searchExecutor = Executors.newSingleThreadExecutor()
    private val wmsExecutor = Executors.newSingleThreadExecutor()
    private val facilityExecutor = Executors.newSingleThreadExecutor()
    private val cctvExecutor = Executors.newSingleThreadExecutor()
    private val securityLightExecutor = Executors.newSingleThreadExecutor()
    private val routeExecutor = Executors.newSingleThreadExecutor()
    private var searching = false
    private lateinit var queryInput: EditText
    private lateinit var searchButton: Button
    private lateinit var searchStatus: TextView
    private lateinit var searchResults: ListView
    private lateinit var safeMapOverlay: ImageView
    private lateinit var facilityOverlay: FrameLayout
    private lateinit var cctvOverlay: CctvOverlayView
    private lateinit var securityLightOverlay: SecurityLightOverlayView
    private lateinit var facilitiesToggle: CheckBox
    private lateinit var cctvToggle: CheckBox
    private lateinit var securityLightsToggle: CheckBox
    private lateinit var safeMapToggle: CheckBox
    private var wmsRequestId = 0
    private var wmsRequest: Future<*>? = null
    private var facilityRequestId = 0
    private var facilityRequest: Future<*>? = null
    private var cctvRequest: Future<*>? = null
    private var cctvSites: List<CctvSite> = emptyList()
    private var securityLightRequest: Future<*>? = null
    private var securityLightSites: List<SecurityLightSite> = emptyList()
    private var facilities: List<Facility> = emptyList()

    // ---- 안심경로 추천 (출발지/도착지, 최단·안전경로) ----
    private lateinit var routeOverlay: RouteOverlayView
    private lateinit var startQuery: EditText
    private lateinit var startSetButton: Button
    private lateinit var endQuery: EditText
    private lateinit var endSetButton: Button
    private lateinit var shortestRouteButton: Button
    private lateinit var safeRouteButton: Button
    private lateinit var resetRouteButton: Button
    private lateinit var routeResultText: TextView

    private var routeGraph: RouteGraph? = null
    private var routeGraphRequest: Future<*>? = null
    private var startPoint: LatLng? = null
    private var endPoint: LatLng? = null
    private var lastShortestResult: RouteAStar.RouteResult? = null
    private var lastSafeResult: RouteAStar.RouteResult? = null
    private var lastDisplayedIsSafe = false
    private var hasDisplayedRoute = false

    private val shortestRouteColor = Color.rgb(198, 40, 40) // 빨강
    private val safeRouteColor = Color.rgb(46, 125, 50)     // 초록

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        val container = findViewById<android.widget.FrameLayout>(R.id.map_container)
        container.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        container.requestApplyInsets()
        status = findViewById(R.id.map_status)
        queryInput = findViewById(R.id.search_query)
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            queryInput.imeHintLocales = LocaleList.forLanguageTags("ko-KR,en-US")
        }
        searchButton = findViewById(R.id.search_button)
        searchStatus = findViewById(R.id.search_status)
        searchResults = findViewById(R.id.search_results)
        safeMapOverlay = findViewById(R.id.safemap_overlay)
        facilityOverlay = findViewById(R.id.facility_overlay)
        cctvOverlay = findViewById(R.id.cctv_overlay)
        securityLightOverlay = findViewById(R.id.security_light_overlay)
        facilitiesToggle = findViewById(R.id.toggle_facilities)
        cctvToggle = findViewById(R.id.toggle_cctv)
        securityLightsToggle = findViewById(R.id.toggle_security_lights)
        safeMapToggle = findViewById(R.id.toggle_safemap)

        routeOverlay = findViewById(R.id.route_overlay)
        startQuery = findViewById(R.id.route_start_query)
        startSetButton = findViewById(R.id.route_start_button)
        endQuery = findViewById(R.id.route_end_query)
        endSetButton = findViewById(R.id.route_end_button)
        shortestRouteButton = findViewById(R.id.route_shortest_button)
        safeRouteButton = findViewById(R.id.route_safe_button)
        resetRouteButton = findViewById(R.id.route_reset_button)
        routeResultText = findViewById(R.id.route_result_text)

        cctvOverlay.onMarkerClick = { site ->
            searchStatus.text = getString(
                R.string.cctv_details,
                site.address,
                site.purpose,
                site.cameraCount,
            )
        }
        securityLightOverlay.onMarkerClick = { site, count ->
            searchStatus.text = getString(
                R.string.security_light_details,
                site.name,
                site.address,
                count,
            )
        }
        facilitiesToggle.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                facilityRequestId++
                facilityRequest?.cancel(true)
                facilityOverlay.visibility = View.INVISIBLE
            } else {
                kakaoMap?.let(::loadNearbyFacilities)
            }
        }
        cctvToggle.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                cctvOverlay.visibility = View.INVISIBLE
            } else {
                val map = kakaoMap ?: return@setOnCheckedChangeListener
                if (cctvSites.isEmpty()) loadCctvSites(map) else showCctvMarkers(map)
            }
        }
        securityLightsToggle.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                securityLightOverlay.visibility = View.INVISIBLE
            } else {
                val map = kakaoMap ?: return@setOnCheckedChangeListener
                if (securityLightSites.isEmpty()) loadSecurityLights(map)
                else showSecurityLightMarkers(map)
            }
        }
        safeMapToggle.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                wmsRequestId++
                wmsRequest?.cancel(true)
                safeMapOverlay.visibility = View.GONE
            } else {
                kakaoMap?.let(::loadSafeMapOverlay)
            }
        }
        searchButton.setOnClickListener { searchPlaces() }
        queryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlaces()
                true
            } else false
        }

        startSetButton.setOnClickListener { searchAndSetRoutePoint(isStart = true) }
        endSetButton.setOnClickListener { searchAndSetRoutePoint(isStart = false) }
        shortestRouteButton.setOnClickListener { showRoute(isSafeRoute = false) }
        safeRouteButton.setOnClickListener { showRoute(isSafeRoute = true) }
        resetRouteButton.setOnClickListener { resetRoute() }

        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
            status.setText(R.string.map_key_missing)
            return
        }

        val map = MapView(this)
        container.addView(map, 0, android.widget.FrameLayout.LayoutParams(-1, -1))
        mapView = map
        map.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() = Unit

            override fun onMapError(error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        kakaoMap = null
                        status.setText(R.string.map_load_failed)
                        status.visibility = View.VISIBLE
                    }
                }
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(kakaoMap: KakaoMap) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        this@MapActivity.kakaoMap = kakaoMap
                        kakaoMap.setGestureEnable(GestureType.Rotate, false)
                        kakaoMap.setGestureEnable(GestureType.RotateZoom, false)
                        kakaoMap.setGestureEnable(GestureType.Tilt, false)
                        kakaoMap.setOnCameraMoveStartListener { _, _ ->
                            wmsRequestId++
                            wmsRequest?.cancel(true)
                            safeMapOverlay.visibility = View.GONE
                            facilityRequestId++
                            facilityRequest?.cancel(true)
                            facilityOverlay.visibility = View.INVISIBLE
                            cctvOverlay.visibility = View.INVISIBLE
                            securityLightOverlay.visibility = View.INVISIBLE
                        }
                        kakaoMap.setOnCameraMoveEndListener { map, _, _ ->
                            loadSafeMapOverlay(map)
                            loadNearbyFacilities(map)
                            showCctvMarkers(map)
                            showSecurityLightMarkers(map)
                            redrawRouteOverlay(map)
                        }
                        status.visibility = View.GONE
                        safeMapOverlay.post { loadSafeMapOverlay(kakaoMap) }
                        safeMapOverlay.post { loadNearbyFacilities(kakaoMap) }
                        safeMapOverlay.post { loadCctvSites(kakaoMap) }
                        safeMapOverlay.post { loadSecurityLights(kakaoMap) }
                    }
                }
            }

            // 천안역을 기본 위치로 사용합니다.
            override fun getPosition(): LatLng = LatLng.from(36.8100, 127.1467)
            override fun getZoomLevel(): Int = 15
        })
    }

    private fun loadNearbyFacilities(map: KakaoMap) {
        if (!facilitiesToggle.isChecked) return
        if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) return
        val width = safeMapOverlay.width
        val height = safeMapOverlay.height
        if (width <= 0 || height <= 0) return
        val topLeft = map.fromScreenPoint(0, 0) ?: return
        val bottomRight = map.fromScreenPoint(width, height) ?: return
        val bounds = WmsBounds(
            west = minOf(topLeft.longitude, bottomRight.longitude),
            south = minOf(topLeft.latitude, bottomRight.latitude),
            east = maxOf(topLeft.longitude, bottomRight.longitude),
            north = maxOf(topLeft.latitude, bottomRight.latitude),
        )
        val requestId = ++facilityRequestId
        facilityRequest?.cancel(true)
        facilityRequest = facilityExecutor.submit {
            val result = runCatching {
                NearbyFacilitySearch(BuildConfig.KAKAO_REST_API_KEY).search(bounds)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || requestId != facilityRequestId) return@runOnUiThread
                result.onSuccess { loaded ->
                    facilities = loaded
                    if (!facilitiesToggle.isChecked) return@onSuccess
                    showFacilityMarkers(map, loaded)
                }.onFailure { error ->
                    Log.e("NearbyFacilities", "Nearby facility search failed", error)
                }
            }
        }
    }

    private fun loadCctvSites(map: KakaoMap) {
        if (!cctvToggle.isChecked || cctvRequest != null || cctvSites.isNotEmpty()) return
        cctvRequest = cctvExecutor.submit {
            val result = runCatching { CheonanCctvRepository().loadNearCheonanStation() }
            runOnUiThread {
                cctvRequest = null
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { sites ->
                    cctvSites = sites
                    if (cctvToggle.isChecked) showCctvMarkers(map)
                }.onFailure { error ->
                    Log.e("CheonanCctv", "CCTV data load failed", error)
                    searchStatus.setText(R.string.cctv_load_failed)
                }
            }
        }
    }

    private fun showCctvMarkers(map: KakaoMap) {
        if (!cctvToggle.isChecked || cctvSites.isEmpty()) return
        val width = cctvOverlay.width
        val height = cctvOverlay.height
        val markers = cctvSites.mapNotNull { site ->
            val point = map.toScreenPoint(LatLng.from(site.latitude, site.longitude))
                ?: return@mapNotNull null
            if (point.x !in 0..width || point.y !in 0..height) return@mapNotNull null
            CctvScreenMarker(point, site)
        }
        cctvOverlay.setMarkers(markers, map.zoomLevel)
        cctvOverlay.visibility = View.VISIBLE
    }

    private fun loadSecurityLights(map: KakaoMap) {
        if (!securityLightsToggle.isChecked || securityLightRequest != null ||
            securityLightSites.isNotEmpty()) return
        securityLightRequest = securityLightExecutor.submit {
            val result = runCatching {
                SecurityLightRepository(assets).loadNearCheonanStation()
            }
            runOnUiThread {
                securityLightRequest = null
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { sites ->
                    securityLightSites = sites
                    if (securityLightsToggle.isChecked) showSecurityLightMarkers(map)
                }.onFailure { error ->
                    Log.e("SecurityLights", "Security light data load failed", error)
                    searchStatus.setText(R.string.security_light_load_failed)
                }
            }
        }
    }

    private fun showSecurityLightMarkers(map: KakaoMap) {
        if (!securityLightsToggle.isChecked || securityLightSites.isEmpty()) return
        val width = securityLightOverlay.width
        val height = securityLightOverlay.height
        val markers = securityLightSites.mapNotNull { site ->
            val point = map.toScreenPoint(LatLng.from(site.latitude, site.longitude))
                ?: return@mapNotNull null
            if (point.x !in 0..width || point.y !in 0..height) return@mapNotNull null
            SecurityLightScreenMarker(point, site)
        }
        securityLightOverlay.setMarkers(markers, map.zoomLevel)
        securityLightOverlay.visibility = View.VISIBLE
    }

    private fun showFacilityMarkers(map: KakaoMap, facilities: List<Facility>) {
        facilityOverlay.removeAllViews()
        val markerScale = markerScaleForZoom(map.zoomLevel)
        val markerWidth = (18 * markerScale * resources.displayMetrics.density).toInt()
        val markerHeight = (22 * markerScale * resources.displayMetrics.density).toInt()
        facilities.forEach { facility ->
            val point = map.toScreenPoint(LatLng.from(facility.latitude, facility.longitude))
                ?: return@forEach
            val marker = ImageView(this).apply {
                setImageResource(markerFor(facility.type))
                contentDescription = facility.name
                x = point.x - markerWidth / 2f
                y = point.y - markerHeight.toFloat()
                setOnClickListener {
                    searchStatus.text = "${facility.name}\n${facility.address}"
                }
            }
            facilityOverlay.addView(marker, FrameLayout.LayoutParams(markerWidth, markerHeight))
        }
        facilityOverlay.visibility = View.VISIBLE
    }

    private fun markerFor(type: FacilityType): Int = when (type) {
        FacilityType.SECURITY -> R.drawable.marker_security
        FacilityType.CONVENIENCE_STORE -> R.drawable.marker_convenience
        FacilityType.FIRE -> R.drawable.marker_fire
    }

    private fun markerScaleForZoom(@Suppress("UNUSED_PARAMETER") zoomLevel: Int): Float = 1f

    private fun loadSafeMapOverlay(map: KakaoMap) {
        if (!safeMapToggle.isChecked) return
        if (BuildConfig.SAFEMAP_SERVICE_KEY.isBlank()) return
        val width = safeMapOverlay.width
        val height = safeMapOverlay.height
        if (width <= 0 || height <= 0) return

        val topLeft = map.fromScreenPoint(0, 0) ?: return
        val bottomRight = map.fromScreenPoint(width, height) ?: return
        val bounds = WmsBounds(
            west = minOf(topLeft.longitude, bottomRight.longitude),
            south = minOf(topLeft.latitude, bottomRight.latitude),
            east = maxOf(topLeft.longitude, bottomRight.longitude),
            north = maxOf(topLeft.latitude, bottomRight.latitude),
        )
        val scale = minOf(1.0, 1024.0 / maxOf(width, height))
        val requestWidth = maxOf(1, (width * scale).toInt())
        val requestHeight = maxOf(1, (height * scale).toInt())
        val requestId = ++wmsRequestId
        wmsRequest?.cancel(true)
        wmsRequest = wmsExecutor.submit {
            val result = runCatching {
                SafeMapWmsClient(BuildConfig.SAFEMAP_SERVICE_KEY)
                    .load(bounds, requestWidth, requestHeight)
            }
            runOnUiThread {
                val bitmap = result.getOrElse { error ->
                    Log.e("SafeMapWms", "WMS image request failed", error)
                    if (requestId == wmsRequestId) {
                        searchStatus.setText(R.string.safemap_load_failed)
                    }
                    return@runOnUiThread
                }
                if (isFinishing || isDestroyed || requestId != wmsRequestId) {
                    bitmap.recycle()
                    return@runOnUiThread
                }
                val previous = safeMapOverlay.drawable
                safeMapOverlay.setImageBitmap(bitmap)
                safeMapOverlay.visibility = View.VISIBLE
                (previous as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?.takeIf { it !== bitmap && !it.isRecycled }
                    ?.recycle()
            }
        }
    }

    private fun searchPlaces() {
        if (searching) return
        val query = queryInput.text.toString().trim()
        if (query.isEmpty()) {
            queryInput.error = getString(R.string.search_empty_query)
            return
        }
        if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) {
            searchStatus.setText(R.string.search_key_missing)
            return
        }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(queryInput.windowToken, 0)
        searching = true
        searchButton.isEnabled = false
        searchResults.visibility = View.GONE
        searchStatus.setText(R.string.search_loading)
        searchExecutor.execute {
            val result = runCatching { KakaoPlaceSearch(BuildConfig.KAKAO_REST_API_KEY).search(query) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                searching = false
                searchButton.isEnabled = true
                result.fold(onSuccess = { places ->
                    searchStatus.text = if (places.isEmpty()) getString(R.string.search_empty)
                    else getString(R.string.search_count, places.size)
                    searchResults.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, places)
                    searchResults.visibility = if (places.isEmpty()) View.GONE else View.VISIBLE
                    searchResults.setOnItemClickListener { _, _, position, _ ->
                        val map = kakaoMap
                        if (map == null) {
                            searchStatus.setText(R.string.search_map_wait)
                        } else {
                            val place = places[position]
                            map.moveCamera(CameraUpdateFactory.newCenterPosition(
                                LatLng.from(place.latitude, place.longitude), 16))
                            searchResults.visibility = View.GONE
                            searchStatus.text = place.toString()
                        }
                    }
                }, onFailure = { error ->
                    searchStatus.setText(when ((error as? PlaceSearchException)?.statusCode) {
                        401, 403 -> R.string.search_auth_error
                        429 -> R.string.search_limit_error
                        else -> R.string.search_failed
                    })
                })
            }
        }
    }

    // ---------------------------------------------------------------------
    // 안심경로 추천: 출발지/도착지 검색, 도로망 로딩, 최단·안전경로 계산 및 표시
    // ---------------------------------------------------------------------

    private fun searchAndSetRoutePoint(isStart: Boolean) {
        val queryField = if (isStart) startQuery else endQuery
        val query = queryField.text.toString().trim()
        if (query.isEmpty()) {
            queryField.error = getString(R.string.search_empty_query)
            return
        }
        if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) {
            routeResultText.setText(R.string.search_key_missing)
            return
        }
        routeResultText.text = getString(R.string.route_point_searching, query)
        searchExecutor.execute {
            val result = runCatching { KakaoPlaceSearch(BuildConfig.KAKAO_REST_API_KEY).search(query) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { places ->
                    val place = places.firstOrNull()
                    if (place == null) {
                        routeResultText.text = getString(R.string.route_point_not_found, query)
                        return@onSuccess
                    }
                    val point = LatLng.from(place.latitude, place.longitude)
                    if (isStart) startPoint = point else endPoint = point
                    routeResultText.text = getString(
                        if (isStart) R.string.route_start_set else R.string.route_end_set,
                        place.name,
                    )
                    kakaoMap?.let { map ->
                        map.moveCamera(CameraUpdateFactory.newCenterPosition(point, 16))
                        redrawRouteOverlay(map)
                    }
                }.onFailure {
                    routeResultText.text = getString(R.string.route_point_not_found, query)
                }
            }
        }
    }

    private fun showRoute(isSafeRoute: Boolean) {
        val map = kakaoMap
        val start = startPoint
        val end = endPoint
        if (map == null) {
            routeResultText.setText(R.string.search_map_wait)
            return
        }
        if (start == null || end == null) {
            routeResultText.setText(R.string.route_need_points)
            return
        }

        val existingGraph = routeGraph
        if (existingGraph != null) {
            computeAndDrawRoutes(map, existingGraph, start, end, isSafeRoute)
            return
        }

        if (routeGraphRequest != null) return
        routeResultText.setText(R.string.route_loading)
        routeGraphRequest = routeExecutor.submit {
            val result = runCatching {
                RoadNetworkProvider.buildGraph(cctvSites, securityLightSites, facilities)
            }
            runOnUiThread {
                routeGraphRequest = null
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { graph ->
                    routeGraph = graph
                    computeAndDrawRoutes(map, graph, start, end, isSafeRoute)
                }.onFailure { error ->
                    Log.e("RoadNetwork", "도로망 로딩 실패", error)
                    routeResultText.setText(R.string.route_network_failed)
                }
            }
        }
    }

    private fun computeAndDrawRoutes(
        map: KakaoMap,
        graph: RouteGraph,
        start: LatLng,
        end: LatLng,
        isSafeRoute: Boolean,
    ) {
        val startNode = graph.nearestNode(start.latitude, start.longitude)
        val endNode = graph.nearestNode(end.latitude, end.longitude)
        if (startNode == null || endNode == null) {
            routeResultText.setText(R.string.route_out_of_area)
            return
        }

        val shortest = RouteAStar.findRoute(graph, startNode.id, endNode.id, safetyWeight = 0.0)
        val safe = RouteAStar.findRoute(graph, startNode.id, endNode.id, safetyWeight = 0.55)
        lastShortestResult = shortest
        lastSafeResult = safe
        lastDisplayedIsSafe = isSafeRoute
        hasDisplayedRoute = true

        val displayed = if (isSafeRoute) safe else shortest
        if (displayed == null) {
            routeResultText.setText(R.string.route_not_found)
            return
        }

        routeResultText.text = buildString {
            shortest?.let {
                append(getString(
                    R.string.route_summary_shortest,
                    it.totalDistanceMeters.toInt(),
                    estimateWalkingMinutes(it.totalDistanceMeters),
                    (it.averageSafetyScore * 100).toInt(),
                ))
            }
            safe?.let {
                if (isNotEmpty()) append(" | ")
                append(getString(
                    R.string.route_summary_safe,
                    it.totalDistanceMeters.toInt(),
                    estimateWalkingMinutes(it.totalDistanceMeters),
                    (it.averageSafetyScore * 100).toInt(),
                ))
            }
        }

        redrawRouteOverlay(map)
    }

    /** 카메라가 움직일 때마다 마지막으로 표시한 경로/출발·도착 마커를 새 화면좌표로 다시 그린다. */
    private fun redrawRouteOverlay(map: KakaoMap) {
        val startScreen = startPoint?.let { map.toScreenPoint(it) }
        val endScreen = endPoint?.let { map.toScreenPoint(it) }
        routeOverlay.setStartEnd(startScreen, endScreen)

        if (!hasDisplayedRoute) return
        val result = if (lastDisplayedIsSafe) lastSafeResult else lastShortestResult
        if (result == null) {
            routeOverlay.setRoute(emptyList(), shortestRouteColor)
            return
        }
        val points = mutableListOf<Point>()
        result.segments.forEachIndexed { index, segment ->
            if (index == 0) {
                map.toScreenPoint(LatLng.from(segment.from.lat, segment.from.lon))?.let { points += it }
            }
            map.toScreenPoint(LatLng.from(segment.to.lat, segment.to.lon))?.let { points += it }
        }
        val color = if (lastDisplayedIsSafe) safeRouteColor else shortestRouteColor
        routeOverlay.setRoute(points, color)
    }

    private fun estimateWalkingMinutes(distanceMeters: Double): Int {
        val metersPerMinute = 67.0
        return kotlin.math.ceil(distanceMeters / metersPerMinute).toInt().coerceAtLeast(1)
    }

    private fun resetRoute() {
        startPoint = null
        endPoint = null
        lastShortestResult = null
        lastSafeResult = null
        hasDisplayedRoute = false
        startQuery.text.clear()
        endQuery.text.clear()
        routeResultText.text = ""
        routeOverlay.clear()
    }

    override fun onResume() {
        super.onResume()
        mapView?.resume()
    }

    override fun onPause() {
        mapView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        searchExecutor.shutdownNow()
        wmsRequestId++
        wmsRequest?.cancel(true)
        wmsExecutor.shutdownNow()
        facilityRequestId++
        facilityRequest?.cancel(true)
        facilityExecutor.shutdownNow()
        cctvRequest?.cancel(true)
        cctvExecutor.shutdownNow()
        securityLightRequest?.cancel(true)
        securityLightExecutor.shutdownNow()
        routeGraphRequest?.cancel(true)
        routeExecutor.shutdownNow()
        (safeMapOverlay.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?.takeIf { !it.isRecycled }?.recycle()
        kakaoMap = null
        mapView?.finish()
        mapView = null
        super.onDestroy()
    }

}