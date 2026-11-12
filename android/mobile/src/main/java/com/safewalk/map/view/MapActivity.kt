package com.safewalk.map.view

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.LocaleList
import android.location.Location
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
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
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.safewalk.BuildConfig
import com.safewalk.R
import com.safewalk.auth.TokenStore
import com.safewalk.common.network.ApiClient
import com.safewalk.emergency.EmergencyLocationService
import com.safewalk.emergency.EmergencyMapActivity
import com.safewalk.emergency.EmergencyRepository
import com.safewalk.emergency.EmergencySessionStore
import com.safewalk.main.navigation.MainTab
import com.safewalk.location.GpsSharingService
import com.safewalk.main.navigation.bindBottomNavigation
import com.safewalk.notification.NotificationBadge
import com.safewalk.notification.NotificationEvents
import com.safewalk.map.model.CctvSite
import com.safewalk.map.model.Facility
import com.safewalk.map.model.FacilityType
import com.safewalk.map.model.HistoryKind
import com.safewalk.map.model.Place
import com.safewalk.map.model.SearchHistoryItem
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class MapActivity : ComponentActivity() {
    private val viewModel: MapViewModel by viewModels()

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
    private lateinit var shortestRouteButton: Button
    private lateinit var safeRouteButton: Button
    private lateinit var resetRouteButton: Button
    private lateinit var guidanceRow: View
    private lateinit var guidanceButton: Button
    private lateinit var guidanceStopButton: Button
    private lateinit var historyList: ListView
    private lateinit var routeResultText: TextView
    private lateinit var sosButton: Button
    private lateinit var sosState: TextView
    private lateinit var myLocationButton: Button
    private lateinit var gpsShareButton: Button
    private lateinit var myLocationStatus: TextView
    private val emergencyRepository by lazy { EmergencyRepository(this) }
    private val emergencySessionStore by lazy { EmergencySessionStore(this) }
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var myLocationMarker: Label? = null
    private var lastMyLocation: Location? = null
    private var myLocationRequested = false
    private var myLocationCentered = false
    private var startSharingAfterPermission = false
    private val myLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { renderMyLocation(it, center = !myLocationCentered) }
        }
    }
    private var sosBusy = false
    private var activeEmergencyId: Long? = null
    private var restoredEmergency = false
    private var checkedSharedEmergencies = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            showSosConfirmation()
        } else {
            Toast.makeText(this, "SOS 위치 공유를 위해 위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    private val mapLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            startMyLocationUpdates()
            refreshMyLocation()
            if (startSharingAfterPermission) startGpsSharing()
        } else {
            myLocationStatus.text = "위치 권한이 필요합니다"
        }
        startSharingAfterPermission = false
    }

    private val gpsStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                GpsSharingService.STATE_STARTED -> myLocationStatus.text = "위치 공유 중"
                GpsSharingService.STATE_SENT -> myLocationStatus.text = "최신 위치 전송됨"
                GpsSharingService.STATE_RETRY -> myLocationStatus.text = "전송 실패 · 재시도 대기"
                GpsSharingService.STATE_STOPPED -> {
                    myLocationStatus.text = intent.getStringExtra(GpsSharingService.EXTRA_MESSAGE) ?: "위치 공유 종료"
                }
            }
            renderGpsShareButton()
        }
    }

    private val emergencyStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(EmergencyLocationService.EXTRA_EMERGENCY_ID, 0L) ?: return
            if (id != activeEmergencyId) return
            when (intent.action) {
                EmergencyLocationService.ACTION_TRACKING_STARTED -> sosState.text = "보호자에게 위치 공유 중"
                EmergencyLocationService.ACTION_LOCATION_SENT -> sosState.text = "SOS 진행 중 · 위치 전송됨"
                EmergencyLocationService.ACTION_LOCATION_FAILED -> sosState.text = "SOS 진행 중 · 위치 재전송 대기"
                EmergencyLocationService.ACTION_TRACKING_STOPPED -> {
                    activeEmergencyId = null
                    renderSosState()
                    intent.getStringExtra(EmergencyLocationService.EXTRA_MESSAGE)?.let {
                        Toast.makeText(this@MapActivity, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationEvents.incoming.collect {
                    NotificationBadge.refresh(this@MapActivity)
                    activeEmergencyId = emergencySessionStore.activeEmergencyId()
                    renderSosState()
                }
            }
        }
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
        shortestRouteButton = findViewById(R.id.route_shortest_button)
        safeRouteButton = findViewById(R.id.route_safe_button)
        resetRouteButton = findViewById(R.id.route_reset_button)
        guidanceRow = findViewById(R.id.route_guidance_row)
        guidanceButton = findViewById(R.id.route_guidance_button)
        guidanceStopButton = findViewById(R.id.route_guidance_stop_button)
        historyList = findViewById(R.id.route_history_list)
        routeResultText = findViewById(R.id.route_result_text)
        sosButton = findViewById(R.id.sos_button)
        sosState = findViewById(R.id.sos_state)
        myLocationButton = findViewById(R.id.my_location_button)
        gpsShareButton = findViewById(R.id.gps_share_button)
        myLocationStatus = findViewById(R.id.my_location_status)
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
        configureHistoryDropdown()
        shortestRouteButton.setOnClickListener { viewModel.showRoute(isSafeRoute = false) }
        safeRouteButton.setOnClickListener { viewModel.showRoute(isSafeRoute = true) }
        resetRouteButton.setOnClickListener {
            viewModel.resetRoute()
            startQuery.text.clear()
            endQuery.text.clear()
            routeResultText.text = ""
            routeOverlay.clear()
        }
        guidanceButton.setOnClickListener {
            if (viewModel.uiState.value.route.guidance == null) {
                viewModel.startGuidance()
            } else {
                viewModel.finishGuidance(completed = true)
            }
        }
        guidanceStopButton.setOnClickListener { viewModel.finishGuidance(completed = false) }
        sosButton.setOnClickListener { onSosClicked() }
        myLocationButton.setOnClickListener { refreshMyLocation() }
        gpsShareButton.setOnClickListener {
            if (GpsSharingService.isSharing(this)) {
                GpsSharingService.stop(this)
                gpsShareButton.isEnabled = false
                myLocationStatus.text = "위치 공유 종료 중…"
            } else if (hasLocationPermission()) {
                startGpsSharing()
            } else {
                startSharingAfterPermission = true
                requestMapLocationPermission()
            }
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
                    lastMyLocation?.let { renderMyLocation(it, center = !myLocationCentered) }
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
        refreshHistoryDropdown()
        renderRouteSummary(state.route)
        renderGuidance(state.route)
    }

    /** 안내 버튼: 경로가 있으면 '안내 시작', 안내 중이면 '도착 완료' + '안내 종료' */
    private fun renderGuidance(route: RouteUiState) {
        val guiding = route.guidance != null
        guidanceRow.visibility = if (guiding || route.displayedResult != null) View.VISIBLE else View.GONE
        guidanceButton.setText(if (guiding) R.string.route_guidance_arrive else R.string.route_guidance_start)
        guidanceStopButton.visibility = if (guiding) View.VISIBLE else View.GONE
        guidanceButton.isEnabled = !route.guidanceBusy
        guidanceStopButton.isEnabled = !route.guidanceBusy
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
            is MapEffect.FillRouteQueries -> {
                effect.startName?.let { startQuery.setText(it) }
                effect.endName?.let { endQuery.setText(it) }
            }
            is MapEffect.ChooseRoutePoint -> showRoutePointPicker(effect.isStart, effect.places)
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

    // ---- 최근 검색(장소·경로) 드롭다운: 출발/도착 입력창을 누르면 아래에 펼쳐진다 ----

    private sealed interface HistoryRow {
        data class Entry(val item: SearchHistoryItem) : HistoryRow
        data object More : HistoryRow
        data object Clear : HistoryRow
    }

    private var historyRows: List<HistoryRow> = emptyList()
    private var historyTargetIsStart = true
    private var historyExpanded = false

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun configureHistoryDropdown() {
        val focusListener = View.OnFocusChangeListener { _, _ -> refreshHistoryDropdown() }
        val textListener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshHistoryDropdown()
        }
        startQuery.onFocusChangeListener = focusListener
        endQuery.onFocusChangeListener = focusListener
        startQuery.addTextChangedListener(textListener)
        endQuery.addTextChangedListener(textListener)
        startQuery.setOnClickListener { refreshHistoryDropdown() }
        endQuery.setOnClickListener { refreshHistoryDropdown() }

        historyList.setOnItemClickListener { _, _, position, _ ->
            when (val row = historyRows.getOrNull(position)) {
                is HistoryRow.Entry -> {
                    viewModel.applyHistory(row.item, historyTargetIsStart)
                    closeHistoryDropdown()
                }
                HistoryRow.More -> {
                    historyExpanded = true
                    refreshHistoryDropdown()
                }
                HistoryRow.Clear -> {
                    viewModel.clearHistory()
                    closeHistoryDropdown()
                }
                null -> Unit
            }
        }
        historyList.setOnItemLongClickListener { _, _, position, _ ->
            val row = historyRows.getOrNull(position) as? HistoryRow.Entry
            if (row != null) viewModel.deleteHistory(row.item)
            row != null
        }
    }

    /** 포커스가 있는 입력창이 비어 있고 기록이 있을 때만 펼친다. */
    private fun refreshHistoryDropdown() {
        val target = when {
            startQuery.hasFocus() -> startQuery
            endQuery.hasFocus() -> endQuery
            else -> null
        }
        val history = viewModel.uiState.value.history
        if (target == null || target.text.isNotBlank() || history.isEmpty()) {
            historyList.visibility = View.GONE
            return
        }
        historyTargetIsStart = target === startQuery
        val hasMore = !historyExpanded && history.size > HISTORY_COLLAPSED_COUNT
        val shown = if (historyExpanded) history else history.take(HISTORY_COLLAPSED_COUNT)
        historyRows = shown.map { HistoryRow.Entry(it) } +
            listOfNotNull(if (hasMore) HistoryRow.More else null, HistoryRow.Clear)
        historyList.adapter = HistoryAdapter(historyRows)
        historyList.visibility = View.VISIBLE
    }

    private fun closeHistoryDropdown() {
        historyExpanded = false
        historyList.visibility = View.GONE
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(startQuery.windowToken, 0)
        currentFocus?.clearFocus()
    }

    private inner class HistoryAdapter(private val rows: List<HistoryRow>) : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView as? TextView) ?: TextView(this@MapActivity).apply {
                minHeight = dp(44)
                gravity = Gravity.CENTER_VERTICAL
                compoundDrawablePadding = dp(12)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                textSize = 15f
            }
            var iconRes = 0
            when (val row = rows[position]) {
                is HistoryRow.Entry -> {
                    val item = row.item
                    if (item.kind == HistoryKind.ROUTE) {
                        // 출발지 → 도착지 (출발지·화살표는 회색, 도착지는 진하게)
                        val prefix = item.name.length + 3
                        view.text = SpannableString("${item.name} → ${item.endName}").apply {
                            setSpan(ForegroundColorSpan(Color.GRAY), 0, prefix, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        iconRes = android.R.drawable.ic_menu_directions
                    } else {
                        view.text = item.name
                        iconRes = android.R.drawable.ic_menu_mylocation
                    }
                    view.setTextColor(Color.rgb(32, 33, 36))
                }
                HistoryRow.More -> {
                    view.text = "더보기 ⌄"
                    view.setTextColor(Color.GRAY)
                }
                HistoryRow.Clear -> {
                    view.text = "전체 삭제 (항목을 길게 누르면 삭제)"
                    view.setTextColor(Color.GRAY)
                }
            }
            val icon = if (iconRes != 0) getDrawable(iconRes)?.mutate()?.apply {
                setTint(Color.GRAY)
                setBounds(0, 0, dp(20), dp(20))
            } else null
            view.setCompoundDrawablesRelative(icon, null, null, null)
            return view
        }
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

    private fun onSosClicked() {
        if (sosBusy) return
        val activeId = activeEmergencyId
        if (activeId != null) {
            openEmergencyMap(activeId)
            return
        }
        if (!TokenStore(this).isLoggedIn()) {
            Toast.makeText(this, "SOS 보호자 알림을 사용하려면 로그인해 주세요.", Toast.LENGTH_LONG).show()
            return
        }
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        } else {
            showSosConfirmation()
        }
    }

    private fun showSosConfirmation() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("SOS를 시작할까요?")
            .setMessage("10초 동안 취소할 수 있습니다. 시작하면 연결된 보호자에게 알림과 현재 위치가 공유됩니다.")
            .setPositiveButton("10초 후 전송", null)
            .setNegativeButton("취소", null)
            .create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            lifecycleScope.launch {
                for (remaining in 10 downTo 1) {
                    if (!dialog.isShowing) return@launch
                    positive.text = "${remaining}초 후 전송"
                    delay(1_000L)
                }
                if (dialog.isShowing) {
                    positive.text = "SOS 전송"
                    positive.isEnabled = true
                    positive.setOnClickListener {
                        dialog.dismiss()
                        collectInitialLocationAndStart()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun collectInitialLocationAndStart() {
        sosBusy = true
        renderSosState("현재 위치 확인 중…")
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            sosBusy = false
            renderSosState()
            Toast.makeText(this, "위치 권한을 확인해 주세요.", Toast.LENGTH_LONG).show()
            return
        }
        val cancellation = CancellationTokenSource()
        var completed = false
        val timeout = Runnable {
            if (!completed) {
                completed = true
                cancellation.cancel()
                createSos(null)
            }
        }
        sosButton.postDelayed(timeout, 10_000L)
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnCompleteListener { task ->
                if (completed) return@addOnCompleteListener
                completed = true
                sosButton.removeCallbacks(timeout)
                createSos(if (task.isSuccessful) task.result else null)
            }
    }

    private fun createSos(location: android.location.Location?) {
        lifecycleScope.launch {
            try {
                val started = withContext(Dispatchers.IO) { emergencyRepository.start(location) }
                activeEmergencyId = started.emergencyId
                emergencySessionStore.setActive(started.emergencyId)
                runCatching { EmergencyLocationService.start(this@MapActivity, started.emergencyId) }
                    .onFailure {
                        Toast.makeText(
                            this@MapActivity,
                            "SOS는 시작됐지만 위치 서비스를 실행하지 못했습니다. 앱을 다시 열어 주세요.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                val notice = if (started.recoveredExisting) {
                    "이미 진행 중인 SOS를 복구해 위치 전송을 다시 시작했습니다."
                } else if (started.pushGuardians == 0) {
                    "SOS가 시작됐습니다. 앱으로 연결된 보호자가 없어 보호자 설정을 확인해 주세요."
                } else {
                    "보호자 ${started.pushGuardians}명에게 SOS 알림을 보냈습니다."
                }
                Toast.makeText(this@MapActivity, notice, Toast.LENGTH_LONG).show()
                openEmergencyMap(started.emergencyId)
            } catch (error: ApiClient.ApiException) {
                Toast.makeText(
                    this@MapActivity,
                    error.message ?: "SOS를 시작하지 못했습니다.",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (error: Exception) {
                Toast.makeText(
                    this@MapActivity,
                    "서버에 연결하지 못했습니다. 네트워크를 확인해 주세요.",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                sosBusy = false
                renderSosState()
            }
        }
    }

    private fun openEmergencyMap(emergencyId: Long) {
        startActivity(
            Intent(this, EmergencyMapActivity::class.java)
                .putExtra(EmergencyMapActivity.EXTRA_EMERGENCY_ID, emergencyId),
        )
    }

    private fun restoreActiveEmergency() {
        if (restoredEmergency || !TokenStore(this).isLoggedIn()) return
        restoredEmergency = true
        lifecycleScope.launch {
            try {
                val active = withContext(Dispatchers.IO) { emergencyRepository.active() }
                if (active == null) {
                    EmergencyLocationService.stop(this@MapActivity)
                    emergencySessionStore.clear()
                    activeEmergencyId = null
                } else {
                    activeEmergencyId = active.emergencyId
                    emergencySessionStore.setActive(active.emergencyId)
                    val hasLocation = ContextCompat.checkSelfPermission(
                        this@MapActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasLocation) EmergencyLocationService.start(this@MapActivity, active.emergencyId)
                }
            } catch (_: Exception) {
                activeEmergencyId = emergencySessionStore.activeEmergencyId()
                activeEmergencyId?.let { id ->
                    val hasLocation = ContextCompat.checkSelfPermission(
                        this@MapActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasLocation) runCatching { EmergencyLocationService.start(this@MapActivity, id) }
                }
            } finally {
                renderSosState()
            }
        }
    }

    private fun checkSharedEmergencies() {
        if (checkedSharedEmergencies || !TokenStore(this).isLoggedIn()) return
        checkedSharedEmergencies = true
        lifecycleScope.launch {
            val shared = runCatching {
                withContext(Dispatchers.IO) { emergencyRepository.shared() }
            }.getOrDefault(emptyList())
            val emergency = shared.firstOrNull { it.canViewLocation } ?: return@launch
            if (isFinishing || isDestroyed) return@launch
            AlertDialog.Builder(this@MapActivity)
                .setTitle("진행 중인 보호 대상자 SOS")
                .setMessage("${emergency.userName}님의 SOS가 진행 중입니다. 실시간 위치를 확인할까요?")
                .setPositiveButton("위치 확인") { _, _ ->
                    startActivity(
                        Intent(this@MapActivity, com.safewalk.emergency.EmergencyMapActivity::class.java)
                            .putExtra(
                                com.safewalk.emergency.EmergencyMapActivity.EXTRA_EMERGENCY_ID,
                                emergency.emergencyId,
                            )
                            .putExtra(
                                com.safewalk.emergency.EmergencyMapActivity.EXTRA_WARD_NAME,
                                emergency.userName,
                            ),
                    )
                }
                .setNegativeButton("나중에", null)
                .show()
        }
    }

    private fun requestNotificationPermissionOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33 || !TokenStore(this).isLoggedIn()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = getSharedPreferences("safewalk_push", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notification_permission_requested", false)) return
        prefs.edit().putBoolean("notification_permission_requested", true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun renderSosState(overrideText: String? = null) {
        if (!::sosButton.isInitialized) return
        sosButton.isEnabled = !sosBusy
        sosButton.text = if (activeEmergencyId == null) "SOS" else "공유 화면"
        sosState.text = overrideText ?: if (activeEmergencyId == null) {
            "긴급 시 누르세요"
        } else {
            "SOS 진행 중 · 위치 공유 중"
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestMapLocationPermission() {
        getSharedPreferences("map_location", MODE_PRIVATE).edit().putBoolean("asked", true).apply()
        mapLocationPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ))
    }

    private fun startMyLocationUpdates() {
        if (myLocationRequested || !hasLocationPermission()) return
        try {
            locationClient.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
                    .setMinUpdateIntervalMillis(9_000L)
                    .build(), myLocationCallback, Looper.getMainLooper(),
            )
            myLocationRequested = true
        } catch (_: SecurityException) {
            myLocationStatus.text = "위치 권한을 확인해 주세요"
        }
    }

    private fun refreshMyLocation() {
        if (!hasLocationPermission()) {
            requestMapLocationPermission()
            return
        }
        myLocationStatus.text = "현재 위치 확인 중…"
        val cancellation = CancellationTokenSource()
        myLocationButton.postDelayed({ cancellation.cancel() }, 10_000L)
        try {
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        renderMyLocation(task.result, center = true)
                    } else {
                        myLocationStatus.text = "현재 위치를 확인하지 못했습니다"
                    }
                }
        } catch (_: SecurityException) {
            myLocationStatus.text = "위치 권한을 확인해 주세요"
        }
    }

    private fun renderMyLocation(location: Location, center: Boolean) {
        lastMyLocation = location
        val map = kakaoMap ?: return
        val position = LatLng.from(location.latitude, location.longitude)
        val existing = myLocationMarker
        if (existing == null) {
            val drawable = requireNotNull(ContextCompat.getDrawable(this, R.drawable.marker_my_location))
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            val style = LabelStyle.from(bitmap).setAnchorPoint(0.5f, 0.5f)
            myLocationMarker = map.labelManager?.layer?.addLabel(
                LabelOptions.from("my-location", position).setStyles(style),
            )
        } else {
            existing.moveTo(position, 800)
        }
        if (center) {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(position, 17))
            myLocationCentered = true
        }
        myLocationStatus.text = if (GpsSharingService.isSharing(this)) "위치 공유 중" else "내 위치 표시 중"
    }

    private fun startGpsSharing() {
        try {
            GpsSharingService.start(this)
            gpsShareButton.text = "위치 공유 종료"
            myLocationStatus.text = "위치 공유 시작 중…"
        } catch (error: Exception) {
            myLocationStatus.text = "위치 공유를 시작하지 못했습니다"
        }
    }

    private fun renderGpsShareButton() {
        gpsShareButton.isEnabled = true
        gpsShareButton.text = if (GpsSharingService.isSharing(this)) "위치 공유 종료" else "위치 공유 시작"
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            emergencyStateReceiver,
            IntentFilter().apply {
                addAction(EmergencyLocationService.ACTION_TRACKING_STARTED)
                addAction(EmergencyLocationService.ACTION_LOCATION_SENT)
                addAction(EmergencyLocationService.ACTION_LOCATION_FAILED)
                addAction(EmergencyLocationService.ACTION_TRACKING_STOPPED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(this, gpsStateReceiver, IntentFilter().apply {
            addAction(GpsSharingService.STATE_STARTED)
            addAction(GpsSharingService.STATE_SENT)
            addAction(GpsSharingService.STATE_RETRY)
            addAction(GpsSharingService.STATE_STOPPED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (hasLocationPermission()) startMyLocationUpdates()
    }

    override fun onStop() {
        unregisterReceiver(emergencyStateReceiver)
        unregisterReceiver(gpsStateReceiver)
        if (myLocationRequested) locationClient.removeLocationUpdates(myLocationCallback)
        myLocationRequested = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        NotificationBadge.refresh(this)
        mapView?.resume()
        activeEmergencyId = emergencySessionStore.activeEmergencyId()
        renderSosState()
        restoreActiveEmergency()
        checkSharedEmergencies()
        requestNotificationPermissionOnce()
        renderGpsShareButton()
        if (!hasLocationPermission()) {
            val prefs = getSharedPreferences("map_location", MODE_PRIVATE)
            if (!prefs.getBoolean("asked", false)) requestMapLocationPermission()
        }
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
        myLocationMarker = null
        mapView?.finish()
        mapView = null
        super.onDestroy()
    }

    private companion object {
        private const val HISTORY_COLLAPSED_COUNT = 5
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
            R.string.route_history_applied,
        )
    }
}
