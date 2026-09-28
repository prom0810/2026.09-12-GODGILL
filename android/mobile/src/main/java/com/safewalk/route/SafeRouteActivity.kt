package com.safewalk.route

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.safewalk.BuildConfig
import com.safewalk.R
import com.safewalk.databinding.ActivitySafeRouteBinding
import com.safewalk.map.MapActivity
import com.safewalk.route.engine.AStarRouter
import com.safewalk.route.engine.KakaoApiClient
import com.safewalk.route.engine.OsmRoadNetworkProvider
import com.safewalk.route.engine.SafeMapWmsClient
import com.safewalk.route.engine.SafetyDataProvider
import com.safewalk.route.engine.SafetyFacilityFinder
import com.safewalk.route.engine.StreetlightProvider
import com.safewalk.route.engine.VWorldBuildingProvider
import com.safewalk.route.ui.WmsGroundOverlay
import kotlinx.coroutines.launch
import kotlin.math.cos
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class SafeRouteActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySafeRouteBinding

    private var graph: SafetyDataProvider.Graph? = null

    // 안전경로 계산에 쓰는 고정 안전 가중치 (RouteMode.STANDARD와 동일한 값)
    private val defaultSafetyWeight = 0.55

    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var routeLines: MutableList<Polyline> = mutableListOf()
    private var facilityMarkers: MutableList<Marker> = mutableListOf()

    // 위험구역(SafeMap) 오버레이 — 계산엔 안 쓰이고 체크박스로 켜고 끄는 시각적 참고용 레이어
    private var dangerZoneOverlay: WmsGroundOverlay? = null

    // 마지막으로 조회된 안전시설 목록(캐시) — 카테고리 체크박스를 눌렀을 때 재조회 없이 바로 필터링해서 다시 그리기 위함
    private var lastFacilities: List<SafetyFacilityFinder.Facility> = emptyList()

    // 결과 상세정보 펼침 상태
    private var isDetailExpanded = false

    // 경로 색상: 최단경로/안전경로를 안전도 그라데이션 대신 유형별 단색 2가지로 구분 (광운대 논문 방식 참고)
    private val shortestRouteColor = 0xFFC62828.toInt() // 빨강
    private val safeRouteColor = 0xFF2E7D32.toInt()     // 초록(앱 기본색과 통일)

    override fun onCreate(savedInstanceState: Bundle?) {
        // osmdroid는 최초 사용 전 Configuration 로드가 필요함 (API 키 불필요)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        super.onCreate(savedInstanceState)
        binding = ActivitySafeRouteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        binding.openFacilityMapButton.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        setupSearchButtons()
        setupButtons()
        setupDangerZoneToggle()
        setupFacilityCategoryToggles()
        setupDetailToggle()
        loadRoadNetwork()
    }

    /**
     * 1) 실제 편의점/지구대·파출소/CCTV 좌표를 대상지역 전체에서 불러오고,
     * 2) 브이월드 건물 데이터(개방감 지수용)와 보안등 좌표(야간 조도용)를 불러오고,
     * 3) 그 정보들이 안전도 점수 계산에 반영된 상태로 OSM 실제 보행자 도로망을 불러온다.
     * 도로를 따라 정확한 경로가 그려지도록 하기 위함 (기존 150m 격자 목업 대체).
     * 네트워크 실패 시에는 격자 목업으로 자동 대체해 앱이 멈추지 않게 한다.
     * (브이월드/공공데이터포털 키가 아직 없으면 해당 항만 조용히 0으로 처리되고 나머지는 정상 동작)
     */
    private fun loadRoadNetwork() {
        setRouteButtonsEnabled(false)
        binding.statusText.text = "도로 데이터를 불러오는 중입니다..."

        lifecycleScope.launch {
            // 안전시설 좌표를 먼저 세팅해야 이후 그래프의 안전도 점수 계산에 반영된다.
            val facilityPoints = runCatching {
                SafetyFacilityFinder.findAllFacilitiesInArea(
                    SafetyDataProvider.CENTER_LAT,
                    SafetyDataProvider.CENTER_LON
                )
            }.getOrDefault(emptyList())
            SafetyDataProvider.setFacilityPoints(facilityPoints)

            // 브이월드 건물 데이터 (개방감 지수). 키 미설정/API 실패 시 빈 리스트 -> 항 자동 무효화.
            val buildings = VWorldBuildingProvider.findAllBuildingsInArea(
                SafetyDataProvider.CENTER_LAT,
                SafetyDataProvider.CENTER_LON
            )
            SafetyDataProvider.setBuildings(buildings)

            // 보안등 좌표 (야간 조도 항). 번들된 실제 CSV(assets)에서 읽으므로 네트워크 불필요.
            val streetlights = StreetlightProvider.findAllStreetlightsInArea(
                this@SafeRouteActivity,
                SafetyDataProvider.CENTER_LAT,
                SafetyDataProvider.CENTER_LON
            )
            SafetyDataProvider.setStreetlights(streetlights)

            var usingRealRoadNetwork = true
            graph = try {
                OsmRoadNetworkProvider.buildGraph(SafetyDataProvider.CENTER_LAT, SafetyDataProvider.CENTER_LON)
            } catch (e: Exception) {
                usingRealRoadNetwork = false
                Toast.makeText(
                    this@SafeRouteActivity,
                    "실제 도로 데이터를 불러오지 못해 임시 격자 데이터로 대체합니다.",
                    Toast.LENGTH_LONG
                ).show()
                SafetyDataProvider.buildGraph()
            }
            setRouteButtonsEnabled(true)

            // 지금 실제 도로 데이터인지 임시 격자인지, 부가 데이터가 몇 개나 반영됐는지 항상 화면에 남김
            val dataSourceLabel = if (usingRealRoadNetwork) "실제 도로 데이터 사용 중" else "⚠️ 임시 격자 데이터 사용 중"
            val buildingLabel = if (buildings.isNotEmpty()) " · 건물 ${buildings.size}개" else ""
            val lightLabel = if (streetlights.isNotEmpty()) " · 보안등 ${streetlights.size}개" else ""
            binding.statusText.text = "${getString(R.string.hint_set_start)} · $dataSourceLabel$buildingLabel$lightLabel"
        }
    }

    private fun setRouteButtonsEnabled(enabled: Boolean) {
        binding.shortestRouteButton.isEnabled = enabled
        binding.safeRouteButton.isEnabled = enabled
    }

    private fun setupMap() {
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.5)
        map.controller.setCenter(GeoPoint(SafetyDataProvider.CENTER_LAT, SafetyDataProvider.CENTER_LON))
    }

    /**
     * 위험구역(행정안전부 생활안전지도 WMS) 오버레이 체크박스.
     * 계산엔 반영하지 않고, 켜면 MVP 대상지역(1.5km) 전체를 덮는 위험도 이미지를 지도 위에 겹쳐 보여준다.
     */
    private fun setupDangerZoneToggle() {
        binding.dangerZoneToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                loadDangerZoneOverlay()
            } else {
                dangerZoneOverlay?.let { binding.mapView.overlays.remove(it) }
                dangerZoneOverlay = null
                binding.mapView.invalidate()
            }
        }
    }

    private fun loadDangerZoneOverlay() {
        if (BuildConfig.SAFEMAP_SERVICE_KEY.isBlank()) {
            Toast.makeText(this, "생활안전지도 서비스키가 설정되지 않았습니다 (local.properties 확인).", Toast.LENGTH_LONG).show()
            binding.dangerZoneToggle.isChecked = false
            return
        }

        // MVP 대상지역(천안역 반경 1.5km)을 감싸는 사각형 범위를 계산
        val radiusM = 1500.0
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * cos(Math.toRadians(SafetyDataProvider.CENTER_LAT)))
        val bounds = SafeMapWmsClient.WmsBounds(
            west = SafetyDataProvider.CENTER_LON - dLon,
            south = SafetyDataProvider.CENTER_LAT - dLat,
            east = SafetyDataProvider.CENTER_LON + dLon,
            north = SafetyDataProvider.CENTER_LAT + dLat
        )

        lifecycleScope.launch {
            val bitmap = SafeMapWmsClient.load(bounds, width = 1024, height = 1024, serviceKey = BuildConfig.SAFEMAP_SERVICE_KEY)
            if (bitmap == null) {
                Toast.makeText(this@SafeRouteActivity, "위험구역 이미지를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                binding.dangerZoneToggle.isChecked = false
                return@launch
            }

            dangerZoneOverlay?.let { binding.mapView.overlays.remove(it) }
            val overlay = WmsGroundOverlay(bounds.west, bounds.south, bounds.east, bounds.north).apply {
                this.bitmap = bitmap
            }
            // 다른 오버레이(마커·경로)보다 아래에 깔리도록 맨 앞(인덱스 0)에 추가
            binding.mapView.overlays.add(0, overlay)
            dangerZoneOverlay = overlay
            binding.mapView.invalidate()
        }
    }

    /** 안전시설 카테고리(편의점·치안시설 / CCTV) 체크박스 — 재조회 없이 캐시된 목록을 필터링해서 다시 그린다. */
    private fun setupFacilityCategoryToggles() {
        val listener = { _: android.widget.CompoundButton, _: Boolean -> renderFacilityMarkers() }
        binding.facilityStorePoliceToggle.setOnCheckedChangeListener(listener)
        binding.facilityCctvToggle.setOnCheckedChangeListener(listener)
    }

    /** "자세히 ▼" / "간략히 ▲" 토글 — 결과 상세정보(안전시설 개수 등) 펼치고 접기 */
    private fun setupDetailToggle() {
        binding.resultToggleButton.setOnClickListener {
            isDetailExpanded = !isDetailExpanded
            applyDetailVisibility()
        }
    }

    private fun applyDetailVisibility() {
        binding.resultDetailText.visibility = if (isDetailExpanded) View.VISIBLE else View.GONE
        binding.resultToggleButton.text = getString(
            if (isDetailExpanded) R.string.btn_hide_detail else R.string.btn_show_detail
        )
    }

    private fun setupSearchButtons() {
        binding.startSearchButton.setOnClickListener {
            val query = binding.startInput.text.toString().trim()
            if (query.isEmpty()) {
                Toast.makeText(this, "출발지 검색어를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            searchAndSetPoint(query, isStart = true)
        }

        binding.endSearchButton.setOnClickListener {
            val query = binding.endInput.text.toString().trim()
            if (query.isEmpty()) {
                Toast.makeText(this, "도착지 검색어를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            searchAndSetPoint(query, isStart = false)
        }
    }

    private fun searchAndSetPoint(query: String, isStart: Boolean) {
        binding.statusText.text = "\"$query\" 검색 중..."

        lifecycleScope.launch {
            try {
                val results = KakaoApiClient.searchKeyword(
                    query,
                    SafetyDataProvider.CENTER_LAT,
                    SafetyDataProvider.CENTER_LON
                )

                if (results.isEmpty()) {
                    binding.statusText.text = "\"$query\" 검색 결과가 없습니다. 다른 검색어를 입력해주세요."
                    return@launch
                }

                // MVP 단계에서는 정확도 1순위 결과를 그대로 사용한다.
                val place = results.first()
                val point = GeoPoint(place.lat, place.lon)

                if (isStart) {
                    startPoint = point
                    startMarker?.let { binding.mapView.overlays.remove(it) }
                    startMarker = addMarker(point, "출발지: ${place.placeName}", null)
                } else {
                    endPoint = point
                    endMarker?.let { binding.mapView.overlays.remove(it) }
                    endMarker = addMarker(point, "도착지: ${place.placeName}", null)
                }

                // 출발지/도착지가 바뀌면 이전에 그려둔 경로/안전시설은 지운다.
                clearPolylines()
                clearFacilityMarkers()
                lastFacilities = emptyList()
                clearResultText()

                binding.mapView.controller.animateTo(point)
                binding.mapView.invalidate()

                binding.statusText.text = "${place.placeName} (${place.addressName})"
            } catch (e: Exception) {
                binding.statusText.text = "검색 중 오류가 발생했습니다: ${e.message}"
            }
        }
    }

    private fun addMarker(point: GeoPoint, title: String, iconRes: Int?): Marker {
        val marker = Marker(binding.mapView)
        marker.position = point
        marker.title = title
        if (iconRes != null) {
            marker.icon = ContextCompat.getDrawable(this, iconRes)
        }
        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate()
        return marker
    }

    private fun setupButtons() {
        binding.shortestRouteButton.setOnClickListener { showRoute(isSafeRoute = false) }
        binding.safeRouteButton.setOnClickListener { showRoute(isSafeRoute = true) }

        binding.resetButton.setOnClickListener {
            clearRoute()
            startPoint = null
            endPoint = null
            lastFacilities = emptyList()
            binding.startInput.text.clear()
            binding.endInput.text.clear()
            binding.statusText.text = getString(R.string.hint_set_start)
            clearResultText()
        }
    }

    private fun clearResultText() {
        binding.resultSummaryText.text = ""
        binding.resultDetailText.text = ""
        binding.resultToggleButton.visibility = View.GONE
        isDetailExpanded = false
        applyDetailVisibility()
    }

    /** 버튼 클릭(또는 슬라이더 조절 후) 시점에 최단경로·안전경로를 함께 계산해 비교 정보를 보여주고, 선택한 쪽만 지도에 그린다. */
    private fun showRoute(isSafeRoute: Boolean) {
        val currentGraph = graph
        if (currentGraph == null) {
            binding.resultSummaryText.text = "도로 데이터를 아직 불러오는 중입니다."
            return
        }

        val start = startPoint
        val end = endPoint
        if (start == null || end == null) {
            binding.resultSummaryText.text = "출발지/도착지를 먼저 검색해 지정해주세요."
            return
        }

        val startNode = SafetyDataProvider.nearestNode(currentGraph, start.latitude, start.longitude)
        val endNode = SafetyDataProvider.nearestNode(currentGraph, end.latitude, end.longitude)

        if (startNode == null || endNode == null) {
            binding.resultSummaryText.text = "MVP 대상 지역(천안역 반경 1.5km) 밖의 지점입니다."
            return
        }

        // 비교를 위해 두 경로를 항상 함께 계산한다.
        val shortestResult = AStarRouter.findRoute(currentGraph, startNode.id, endNode.id, safetyWeight = 0.0)
        val safeResult = AStarRouter.findRoute(currentGraph, startNode.id, endNode.id, safetyWeight = defaultSafetyWeight)

        val displayedResult = if (isSafeRoute) safeResult else shortestResult
        if (displayedResult == null) {
            binding.resultSummaryText.text = "경로를 찾을 수 없습니다."
            return
        }

        drawRoute(displayedResult, if (isSafeRoute) safeRouteColor else shortestRouteColor)
        lastFacilities = emptyList()
        clearFacilityMarkers()

        binding.resultSummaryText.text = buildSummaryText(shortestResult, safeResult, isSafeRoute)
        binding.resultDetailText.text = "표시 중: ${if (isSafeRoute) "안전경로" else "최단경로"}"
        binding.resultToggleButton.visibility = View.VISIBLE

        if (isSafeRoute) {
            findAndShowFacilities(displayedResult.pathNodes())
        }
    }

    /** 최단경로·안전경로의 거리·시간·안전도를 한 줄로 압축한 요약 텍스트를 만든다. */
    private fun buildSummaryText(
        shortestResult: AStarRouter.RouteResult?,
        safeResult: AStarRouter.RouteResult?,
        isSafeRouteDisplayed: Boolean
    ): String {
        val parts = mutableListOf<String>()

        if (shortestResult != null) {
            parts.add(
                "최단 %.0fm·%d분·%.0f점".format(
                    shortestResult.totalDistanceMeters,
                    estimateWalkingMinutes(shortestResult.totalDistanceMeters),
                    shortestResult.averageSafetyScore * 100
                )
            )
        }
        if (safeResult != null) {
            parts.add(
                "안전 %.0fm·%d분·%.0f점".format(
                    safeResult.totalDistanceMeters,
                    estimateWalkingMinutes(safeResult.totalDistanceMeters),
                    safeResult.averageSafetyScore * 100
                )
            )
        }

        return parts.joinToString(" | ")
    }

    private fun findAndShowFacilities(pathNodes: List<com.safewalk.route.model.Node>) {
        binding.resultDetailText.text = "표시 중: 안전경로\n경로 주변 안전시설 확인 중..."

        lifecycleScope.launch {
            try {
                lastFacilities = SafetyFacilityFinder.findNearbyFacilities(pathNodes)
                renderFacilityMarkers()

                val storeCount = lastFacilities.count { it.type == SafetyFacilityFinder.FacilityType.CONVENIENCE_STORE }
                val policeCount = lastFacilities.count { it.type == SafetyFacilityFinder.FacilityType.POLICE }
                val cctvCount = lastFacilities.count { it.type == SafetyFacilityFinder.FacilityType.CCTV }

                binding.resultDetailText.text =
                    "표시 중: 안전경로\n경로 주변: 편의점·치안시설 ${storeCount + policeCount}곳, CCTV ${cctvCount}곳"
            } catch (e: Exception) {
                binding.resultDetailText.text = "표시 중: 안전경로\n안전시설 조회 중 오류: ${e.message}"
            }
        }
    }

    /** 카테고리 체크박스 상태에 맞춰, 캐시된 lastFacilities만으로 마커를 다시 그린다 (재조회 없음). */
    private fun renderFacilityMarkers() {
        clearFacilityMarkers()
        if (lastFacilities.isEmpty()) return

        val showStorePolice = binding.facilityStorePoliceToggle.isChecked
        val showCctv = binding.facilityCctvToggle.isChecked

        for (facility in lastFacilities) {
            val isStorePolice = facility.type == SafetyFacilityFinder.FacilityType.CONVENIENCE_STORE ||
                facility.type == SafetyFacilityFinder.FacilityType.POLICE
            val isCctv = facility.type == SafetyFacilityFinder.FacilityType.CCTV

            if ((isStorePolice && !showStorePolice) || (isCctv && !showCctv)) continue

            val iconRes = when (facility.type) {
                SafetyFacilityFinder.FacilityType.CONVENIENCE_STORE -> R.drawable.route_ic_store_marker
                SafetyFacilityFinder.FacilityType.POLICE -> R.drawable.route_ic_police_marker
                SafetyFacilityFinder.FacilityType.CCTV -> R.drawable.route_ic_cctv_marker
            }
            val marker = addMarker(
                GeoPoint(facility.lat, facility.lon),
                "${facility.type.label}: ${facility.name}",
                iconRes
            )
            facilityMarkers.add(marker)
        }
    }

    /** 평균 보행 속도(분당 약 67m, 시속 약 4km) 기준 예상 소요시간(분)을 계산한다. */
    private fun estimateWalkingMinutes(distanceMeters: Double): Int {
        val metersPerMinute = 67.0
        return kotlin.math.ceil(distanceMeters / metersPerMinute).toInt().coerceAtLeast(1)
    }

    /** 경로 전체를 하나의 색으로 그린다 (최단=빨강 / 안전=초록, 광운대 논문의 2색 구분 방식 참고). */
    private fun drawRoute(result: AStarRouter.RouteResult, routeColor: Int) {
        clearPolylines()
        for (seg in result.segments) {
            val line = Polyline().apply {
                setPoints(
                    listOf(
                        GeoPoint(seg.from.lat, seg.from.lon),
                        GeoPoint(seg.to.lat, seg.to.lon)
                    )
                )
                color = routeColor
                width = 10f
            }
            binding.mapView.overlays.add(line)
            routeLines.add(line)
        }
        binding.mapView.invalidate()
    }

    private fun clearPolylines() {
        routeLines.forEach { binding.mapView.overlays.remove(it) }
        routeLines.clear()
        binding.mapView.invalidate()
    }

    private fun clearFacilityMarkers() {
        facilityMarkers.forEach { binding.mapView.overlays.remove(it) }
        facilityMarkers.clear()
        binding.mapView.invalidate()
    }

    private fun clearRoute() {
        clearPolylines()
        clearFacilityMarkers()
        startMarker?.let { binding.mapView.overlays.remove(it) }
        endMarker?.let { binding.mapView.overlays.remove(it) }
        startMarker = null
        endMarker = null
        binding.mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
