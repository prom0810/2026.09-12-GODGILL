package com.safewalk.map.view

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.LocaleList
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.safewalk.BuildConfig
import com.safewalk.R
import com.safewalk.main.navigation.MainTab
import com.safewalk.main.navigation.bindBottomNavigation
import com.safewalk.map.model.CctvSite
import com.safewalk.map.model.Facility
import com.safewalk.map.model.FacilityType
import com.safewalk.map.model.Place
import com.safewalk.map.model.SecurityLightSite
import com.safewalk.map.model.WmsBounds
import com.safewalk.map.viewmodel.GeoPoint
import com.safewalk.map.viewmodel.MapEffect
import com.safewalk.map.viewmodel.MapUiState
import com.safewalk.map.viewmodel.MapViewModel
import com.safewalk.map.viewmodel.MapViewport
import com.safewalk.map.viewmodel.RouteUiState
import com.safewalk.map.viewmodel.UiText
import kotlinx.coroutines.launch
import kotlin.math.ceil

class MapActivity : ComponentActivity() {
    private val viewModel: MapViewModel by viewModels()
    private val routeComparisonLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val isSafeRoute = result.data?.getBooleanExtra(
            RouteComparisonActivity.EXTRA_IS_SAFE_ROUTE,
            true,
        ) ?: return@registerForActivityResult
        viewModel.setDisplayedRoute(isSafeRoute)
    }

    private var mapView: MapView? = null
    private var kakaoMap: KakaoMap? = null
    private lateinit var status: TextView
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
    private lateinit var routeOverlay: RouteOverlayView
    private lateinit var startQuery: EditText
    private lateinit var startSetButton: Button
    private lateinit var endQuery: EditText
    private lateinit var endSetButton: Button
    private lateinit var compareRouteButton: Button
    private lateinit var resetRouteButton: Button
    private lateinit var routeResultText: TextView

    private val shortestRouteColor = Color.rgb(198, 40, 40)
    private val safeRouteColor = Color.rgb(46, 125, 50)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        bindBottomNavigation(MainTab.MAP)
        bindViews()
        configureSystemBars()
        configureInteractions()
        collectViewModel()
        startMap()
    }

    private fun bindViews() {
        status = findViewById(R.id.map_status)
        queryInput = findViewById(R.id.search_query)
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
        compareRouteButton = findViewById(R.id.route_compare_button)
        resetRouteButton = findViewById(R.id.route_reset_button)
        routeResultText = findViewById(R.id.route_result_text)
    }

    private fun configureSystemBars() {
        val container = findViewById<FrameLayout>(R.id.map_container)
        container.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        container.requestApplyInsets()
    }

    private fun configureInteractions() {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            queryInput.imeHintLocales = LocaleList.forLanguageTags("ko-KR,en-US")
        }
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
            viewModel.setFacilitiesEnabled(checked)
            facilityOverlay.visibility = if (checked) View.VISIBLE else View.INVISIBLE
        }
        cctvToggle.setOnCheckedChangeListener { _, checked ->
            viewModel.setCctvEnabled(checked)
            cctvOverlay.visibility = if (checked) View.VISIBLE else View.INVISIBLE
        }
        securityLightsToggle.setOnCheckedChangeListener { _, checked ->
            viewModel.setSecurityLightsEnabled(checked)
            securityLightOverlay.visibility = if (checked) View.VISIBLE else View.INVISIBLE
        }
        safeMapToggle.setOnCheckedChangeListener { _, checked ->
            viewModel.setSafeMapEnabled(checked)
            if (!checked) safeMapOverlay.visibility = View.GONE
        }
        searchButton.setOnClickListener { searchPlaces() }
        queryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlaces()
                true
            } else {
                false
            }
        }
        startSetButton.setOnClickListener {
            viewModel.searchRoutePoint(startQuery.text.toString(), isStart = true)
        }
        endSetButton.setOnClickListener {
            viewModel.searchRoutePoint(endQuery.text.toString(), isStart = false)
        }
        compareRouteButton.setOnClickListener { viewModel.compareRoutes() }
        resetRouteButton.setOnClickListener {
            viewModel.resetRoute()
            startQuery.text.clear()
            endQuery.text.clear()
            routeResultText.text = ""
            routeOverlay.clear()
        }
    }

    private fun collectViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderState) }
                launch { viewModel.effects.collect(::handleEffect) }
            }
        }
    }

    private fun startMap() {
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
            status.setText(R.string.map_key_missing)
            return
        }
        val map = MapView(this)
        findViewById<FrameLayout>(R.id.map_container).addView(
            map,
            0,
            FrameLayout.LayoutParams(-1, -1),
        )
        mapView = map
        map.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() = Unit

            override fun onMapError(error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        kakaoMap = null
                        viewModel.onMapUnavailable()
                        status.setText(R.string.map_load_failed)
                        status.visibility = View.VISIBLE
                    }
                }
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(kakaoMap: KakaoMap) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    this@MapActivity.kakaoMap = kakaoMap
                    configureMapGestures(kakaoMap)
                    viewModel.onMapReady()
                    status.visibility = View.GONE
                    safeMapOverlay.post {
                        viewportFor(kakaoMap)?.let(viewModel::onViewportChanged)
                    }
                }
            }

            override fun getPosition(): LatLng = LatLng.from(36.8100, 127.1467)
            override fun getZoomLevel(): Int = 15
        })
    }

    private fun configureMapGestures(map: KakaoMap) {
        map.setGestureEnable(GestureType.Rotate, false)
        map.setGestureEnable(GestureType.RotateZoom, false)
        map.setGestureEnable(GestureType.Tilt, false)
        map.setOnCameraMoveStartListener { _, _ ->
            viewModel.onCameraMoveStarted()
            safeMapOverlay.visibility = View.GONE
            facilityOverlay.visibility = View.INVISIBLE
            cctvOverlay.visibility = View.INVISIBLE
            securityLightOverlay.visibility = View.INVISIBLE
        }
        map.setOnCameraMoveEndListener { movedMap, _, _ ->
            viewportFor(movedMap)?.let(viewModel::onViewportChanged)
            renderMapLayers(viewModel.uiState.value, movedMap)
        }
    }

    private fun viewportFor(map: KakaoMap): MapViewport? {
        val width = safeMapOverlay.width
        val height = safeMapOverlay.height
        if (width <= 0 || height <= 0) return null
        val topLeft = map.fromScreenPoint(0, 0) ?: return null
        val bottomRight = map.fromScreenPoint(width, height) ?: return null
        val bounds = WmsBounds(
            west = minOf(topLeft.longitude, bottomRight.longitude),
            south = minOf(topLeft.latitude, bottomRight.latitude),
            east = maxOf(topLeft.longitude, bottomRight.longitude),
            north = maxOf(topLeft.latitude, bottomRight.latitude),
        )
        val scale = minOf(1.0, 1024.0 / maxOf(width, height))
        return MapViewport(
            bounds = bounds,
            width = maxOf(1, (width * scale).toInt()),
            height = maxOf(1, (height * scale).toInt()),
        )
    }

    private fun renderState(state: MapUiState) {
        searchButton.isEnabled = !state.isSearching
        renderSearchResults(state.searchResults)
        kakaoMap?.let { renderMapLayers(state, it) }
        renderRouteSummary(state.route)
    }

    private fun renderSearchResults(places: List<Place>) {
        searchResults.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, places)
        searchResults.visibility = if (places.isEmpty()) View.GONE else View.VISIBLE
        searchResults.setOnItemClickListener { _, _, position, _ ->
            places.getOrNull(position)?.let(viewModel::selectSearchResult)
        }
    }

    private fun renderMapLayers(state: MapUiState, map: KakaoMap) {
        if (state.facilitiesEnabled) {
            showFacilityMarkers(map, state.facilities)
        } else {
            facilityOverlay.visibility = View.INVISIBLE
        }
        if (state.cctvEnabled) {
            showCctvMarkers(map, state.cctvSites)
        } else {
            cctvOverlay.visibility = View.INVISIBLE
        }
        if (state.securityLightsEnabled) {
            showSecurityLightMarkers(map, state.securityLightSites)
        } else {
            securityLightOverlay.visibility = View.INVISIBLE
        }
        redrawRouteOverlay(map, state.route)
    }

    private fun handleEffect(effect: MapEffect) {
        when (effect) {
            is MapEffect.SafeMapImageReady -> {
                if (!viewModel.uiState.value.safeMapEnabled) {
                    effect.bitmap.recycle()
                    return
                }
                val previous = safeMapOverlay.drawable
                safeMapOverlay.setImageBitmap(effect.bitmap)
                safeMapOverlay.visibility = View.VISIBLE
                (previous as? BitmapDrawable)?.bitmap
                    ?.takeIf { it !== effect.bitmap && !it.isRecycled }
                    ?.recycle()
            }
            is MapEffect.MoveCamera -> {
                val map = kakaoMap
                if (map == null) {
                    showMessage(UiText(R.string.search_map_wait))
                } else {
                    map.moveCamera(
                        CameraUpdateFactory.newCenterPosition(
                            LatLng.from(effect.point.latitude, effect.point.longitude),
                            16,
                        ),
                    )
                }
            }
            is MapEffect.ChooseRoutePoint -> showRoutePointPicker(effect.isStart, effect.places)
            is MapEffect.OpenRouteComparison -> routeComparisonLauncher.launch(
                RouteComparisonActivity.createIntent(this, effect),
            )
            is MapEffect.ShowMessage -> showMessage(effect.text)
        }
    }

    private fun showRoutePointPicker(isStart: Boolean, places: List<Place>) {
        val labels = places.map { "${it.name}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (isStart) "출발지 선택" else "도착지 선택")
            .setItems(labels) { _, index -> viewModel.selectRoutePoint(places[index], isStart) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showMessage(text: UiText) {
        val message = getString(text.resourceId, *text.arguments.toTypedArray())
        if (text.resourceId in routeMessageResources) {
            routeResultText.text = message
        } else {
            searchStatus.text = message
        }
    }

    private fun searchPlaces() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(queryInput.windowToken, 0)
        viewModel.searchPlaces(queryInput.text.toString())
    }

    private fun showCctvMarkers(map: KakaoMap, sites: List<CctvSite>) {
        if (sites.isEmpty()) {
            cctvOverlay.visibility = View.INVISIBLE
            return
        }
        val width = cctvOverlay.width
        val height = cctvOverlay.height
        val markers = sites.mapNotNull { site ->
            val point = map.toScreenPoint(LatLng.from(site.latitude, site.longitude))
                ?: return@mapNotNull null
            if (point.x !in 0..width || point.y !in 0..height) return@mapNotNull null
            CctvScreenMarker(point, site)
        }
        cctvOverlay.setMarkers(markers, map.zoomLevel)
        cctvOverlay.visibility = View.VISIBLE
    }

    private fun showSecurityLightMarkers(map: KakaoMap, sites: List<SecurityLightSite>) {
        if (sites.isEmpty()) {
            securityLightOverlay.visibility = View.INVISIBLE
            return
        }
        val width = securityLightOverlay.width
        val height = securityLightOverlay.height
        val markers = sites.mapNotNull { site ->
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
        val markerWidth = (18 * resources.displayMetrics.density).toInt()
        val markerHeight = (22 * resources.displayMetrics.density).toInt()
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

    private fun redrawRouteOverlay(map: KakaoMap, route: RouteUiState) {
        val startScreen = route.startPoint?.let {
            map.toScreenPoint(LatLng.from(it.latitude, it.longitude))
        }
        val endScreen = route.endPoint?.let {
            map.toScreenPoint(LatLng.from(it.latitude, it.longitude))
        }
        routeOverlay.setStartEnd(startScreen, endScreen)
        val result = route.displayedResult
        if (result == null) {
            routeOverlay.setRoute(emptyList(), shortestRouteColor)
            return
        }
        val points = mutableListOf<Point>()
        result.segments.forEachIndexed { index, segment ->
            if (index == 0) {
                map.toScreenPoint(LatLng.from(segment.from.lat, segment.from.lon))?.let(points::add)
            }
            map.toScreenPoint(LatLng.from(segment.to.lat, segment.to.lon))?.let(points::add)
        }
        routeOverlay.setRoute(
            points,
            if (route.displayedIsSafe) safeRouteColor else shortestRouteColor,
        )
    }

    private fun renderRouteSummary(route: RouteUiState) {
        if (route.isLoading || route.shortestResult == null && route.safeResult == null) return
        routeResultText.text = buildString {
            route.shortestResult?.let {
                append(getString(
                    R.string.route_summary_shortest,
                    it.totalDistanceMeters.toInt(),
                    estimateWalkingMinutes(it.totalDistanceMeters),
                    (it.averageSafetyScore * 100).toInt(),
                ))
            }
            route.safeResult?.let {
                if (isNotEmpty()) append(" | ")
                append(getString(
                    R.string.route_summary_safe,
                    it.totalDistanceMeters.toInt(),
                    estimateWalkingMinutes(it.totalDistanceMeters),
                    (it.averageSafetyScore * 100).toInt(),
                ))
            }
        }
    }

    private fun estimateWalkingMinutes(distanceMeters: Double): Int =
        ceil(distanceMeters / 67.0).toInt().coerceAtLeast(1)

    override fun onResume() {
        super.onResume()
        mapView?.resume()
    }

    override fun onPause() {
        mapView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        (safeMapOverlay.drawable as? BitmapDrawable)?.bitmap
            ?.takeIf { !it.isRecycled }
            ?.recycle()
        kakaoMap = null
        mapView?.finish()
        mapView = null
        super.onDestroy()
    }

    private companion object {
        val routeMessageResources = setOf(
            R.string.route_point_searching,
            R.string.route_point_not_found,
            R.string.route_start_set,
            R.string.route_end_set,
            R.string.route_need_points,
            R.string.route_loading,
            R.string.route_network_failed,
            R.string.route_out_of_area,
            R.string.route_not_found,
        )
    }
}
