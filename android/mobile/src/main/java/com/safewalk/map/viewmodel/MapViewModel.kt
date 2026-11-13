package com.safewalk.map.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safewalk.BuildConfig
import com.safewalk.R
import com.safewalk.auth.TokenStore
import com.safewalk.common.network.ApiClient
import com.safewalk.map.model.DefaultMapRepository
import com.safewalk.map.model.MapRepository
import com.safewalk.map.model.Place
import com.safewalk.map.model.PlaceSearchException
import com.safewalk.map.model.RouteAStar
import com.safewalk.map.model.HistoryKind
import com.safewalk.map.model.RouteGraph
import com.safewalk.map.model.RouteGuidanceRepository
import com.safewalk.map.model.MAX_ROUTE_POINTS
import com.safewalk.map.model.SearchHistoryItem
import com.safewalk.map.model.SearchHistoryStore
import com.safewalk.watch.GuidanceStore
import com.safewalk.watch.WatchConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MapRepository = DefaultMapRepository(
        safeMapServiceKey = BuildConfig.SAFEMAP_SERVICE_KEY,
        kakaoRestApiKey = BuildConfig.KAKAO_REST_API_KEY,
        assets = application.assets,
    )

    private val historyStore = SearchHistoryStore(application)
    private val guidanceRepository = RouteGuidanceRepository(application)
    private val tokenStore = TokenStore(application)

    private val _uiState = MutableStateFlow(MapUiState(history = historyStore.load()))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _effects = Channel<MapEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var lastViewport: MapViewport? = null
    private var routeGraph: RouteGraph? = null
    private var safeMapJob: Job? = null
    private var facilityJob: Job? = null
    private var cctvJob: Job? = null
    private var securityLightJob: Job? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var guidanceJob: Job? = null

    init {
        restoreActiveGuidance()
    }

    fun onMapReady() {
        _uiState.update { it.copy(isMapReady = true) }
        loadCctvSites()
        loadSecurityLights()
    }

    fun onMapUnavailable() {
        _uiState.update { it.copy(isMapReady = false) }
    }

    fun onViewportChanged(viewport: MapViewport) {
        lastViewport = viewport
        loadSafeMap(viewport)
        loadFacilities(viewport)
    }

    fun onCameraMoveStarted() {
        safeMapJob?.cancel()
        facilityJob?.cancel()
    }

    fun setFacilitiesEnabled(enabled: Boolean) {
        _uiState.update { it.copy(facilitiesEnabled = enabled) }
        if (enabled) lastViewport?.let(::loadFacilities) else facilityJob?.cancel()
    }

    fun setCctvEnabled(enabled: Boolean) {
        _uiState.update { it.copy(cctvEnabled = enabled) }
        if (enabled) loadCctvSites() else cctvJob?.cancel()
    }

    fun setSecurityLightsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(securityLightsEnabled = enabled) }
        if (enabled) loadSecurityLights() else securityLightJob?.cancel()
    }

    fun setSafeMapEnabled(enabled: Boolean) {
        _uiState.update { it.copy(safeMapEnabled = enabled) }
        if (enabled) lastViewport?.let(::loadSafeMap) else safeMapJob?.cancel()
    }

    fun searchPlaces(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            showMessage(R.string.search_empty_query)
            return
        }
        if (!repository.canSearchPlaces) {
            showMessage(R.string.search_key_missing)
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchResults = emptyList()) }
            showMessage(R.string.search_loading)
            runCatchingIo { repository.searchPlaces(trimmed) }
                .onSuccess { places ->
                    _uiState.update { it.copy(isSearching = false, searchResults = places) }
                    showMessage(if (places.isEmpty()) R.string.search_empty else R.string.search_count, places.size)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSearching = false) }
                    showMessage(when ((error as? PlaceSearchException)?.statusCode) {
                        401, 403 -> R.string.search_auth_error
                        429 -> R.string.search_limit_error
                        else -> R.string.search_failed
                    })
                }
        }
    }

    fun selectSearchResult(place: Place) {
        _uiState.update { it.copy(searchResults = emptyList()) }
        emitEffect(MapEffect.MoveCamera(place.toGeoPoint()))
    }

    fun searchRoutePoint(query: String, isStart: Boolean) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            showMessage(R.string.search_empty_query)
            return
        }
        if (!repository.canSearchPlaces) {
            showMessage(R.string.search_key_missing)
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            showMessage(R.string.route_point_searching, trimmed)
            runCatchingIo { repository.searchPlaces(trimmed) }
                .onSuccess { places ->
                    when {
                        places.isEmpty() -> showMessage(R.string.route_point_not_found, trimmed)
                        places.size == 1 -> selectRoutePoint(places[0], isStart)
                        else -> emitEffect(MapEffect.ChooseRoutePoint(isStart, places))
                    }
                }
                .onFailure { showMessage(R.string.route_point_not_found, trimmed) }
        }
    }

    fun selectRoutePoint(place: Place, isStart: Boolean) {
        val point = place.toGeoPoint()
        _uiState.update { state ->
            state.copy(route = if (isStart) {
                state.route.copy(startPoint = point, startName = place.name, shortestResult = null, safeResult = null)
            } else {
                state.route.copy(endPoint = point, endName = place.name, shortestResult = null, safeResult = null)
            })
        }
        saveHistory(SearchHistoryItem.place(place))
        showMessage(if (isStart) R.string.route_start_set else R.string.route_end_set, place.name)
        emitEffect(MapEffect.MoveCamera(point))
    }

    fun showRoute(isSafeRoute: Boolean) {
        val state = _uiState.value
        val start = state.route.startPoint
        val end = state.route.endPoint
        if (!state.isMapReady) {
            showMessage(R.string.search_map_wait)
            return
        }
        if (start == null || end == null) {
            showMessage(R.string.route_need_points)
            return
        }
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _uiState.update { it.copy(route = it.route.copy(isLoading = true)) }
            showMessage(R.string.route_loading)
            runCatchingIo {
                val graph = routeGraph ?: repository.buildRouteGraph(
                    state.cctvSites,
                    state.securityLightSites,
                    state.facilities,
                ).also { routeGraph = it }
                val startNode = graph.nearestNode(start.latitude, start.longitude)
                val endNode = graph.nearestNode(end.latitude, end.longitude)
                if (startNode == null || endNode == null) return@runCatchingIo RouteCalculation.OutOfArea
                RouteCalculation.Complete(
                    shortest = RouteAStar.findRoute(graph, startNode.id, endNode.id, safetyWeight = 0.0),
                    safe = RouteAStar.findRoute(graph, startNode.id, endNode.id, safetyWeight = 0.55),
                )
            }.onSuccess { result ->
                when (result) {
                    RouteCalculation.OutOfArea -> {
                        _uiState.update { it.copy(route = it.route.copy(isLoading = false)) }
                        showMessage(R.string.route_out_of_area)
                    }
                    is RouteCalculation.Complete -> {
                        if (result.shortest != null || result.safe != null) saveRouteHistory(state.route)
                        _uiState.update {
                            it.copy(route = it.route.copy(
                                shortestResult = result.shortest,
                                safeResult = result.safe,
                                displayedIsSafe = isSafeRoute,
                                isLoading = false,
                            ))
                        }
                        if ((if (isSafeRoute) result.safe else result.shortest) == null) {
                            showMessage(R.string.route_not_found)
                        }
                    }
                }
            }.onFailure {
                _uiState.update { it.copy(route = it.route.copy(isLoading = false)) }
                showMessage(R.string.route_network_failed)
            }
        }
    }

    private fun saveHistory(item: SearchHistoryItem) {
        val next = historyStore.add(item)
        _uiState.update { it.copy(history = next) }
    }

    /** 경로 계산에 성공한 출발지→도착지를 '경로' 기록으로 저장한다. */
    private fun saveRouteHistory(route: RouteUiState) {
        val start = route.startPoint ?: return
        val end = route.endPoint ?: return
        saveHistory(SearchHistoryItem.route(
            startName = route.startName.ifBlank { "출발지" }, startLat = start.latitude, startLng = start.longitude,
            endName = route.endName.ifBlank { "도착지" }, endLat = end.latitude, endLng = end.longitude,
        ))
    }

    /**
     * 기록을 눌렀을 때. 장소(PLACE)는 눌러 둔 입력창(isStart)의 출발/도착으로,
     * 경로(ROUTE)는 출발·도착을 한 번에 복원한다(경로는 버튼으로 다시 계산).
     */
    fun applyHistory(item: SearchHistoryItem, isStart: Boolean) {
        when (item.kind) {
            HistoryKind.PLACE -> {
                selectRoutePoint(item.toPlace(), isStart)
                emitEffect(MapEffect.FillRouteQueries(
                    startName = if (isStart) item.name else null,
                    endName = if (isStart) null else item.name,
                ))
            }
            HistoryKind.ROUTE -> {
                val start = GeoPoint(item.latitude, item.longitude)
                val end = GeoPoint(item.endLatitude, item.endLongitude)
                routeJob?.cancel()
                _uiState.update {
                    it.copy(route = RouteUiState(
                        startPoint = start, endPoint = end,
                        startName = item.name, endName = item.endName,
                        guidance = it.route.guidance,
                        guidanceBusy = it.route.guidanceBusy,
                    ))
                }
                saveHistory(item.copy(savedAt = System.currentTimeMillis()))
                emitEffect(MapEffect.FillRouteQueries(item.name, item.endName))
                emitEffect(MapEffect.MoveCamera(start))
                showMessage(R.string.route_history_applied, item.name, item.endName)
            }
        }
    }

    fun deleteHistory(item: SearchHistoryItem) {
        _uiState.update { it.copy(history = historyStore.remove(item)) }
    }

    fun clearHistory() {
        historyStore.clear()
        _uiState.update { it.copy(history = emptyList()) }
    }

    fun resetRoute() {
        routeJob?.cancel()
        // 안내 중에 초기화하면 서버 기록도 '안내 취소'로 닫는다
        _uiState.value.route.guidance?.let { finishGuidance(completed = false) }
        _uiState.update { it.copy(route = RouteUiState(guidance = it.route.guidance, guidanceBusy = it.route.guidanceBusy)) }
    }

    // ---- 경로 안내 기록 (서버 /api/routes) ----

    /** 화면에 보이는 경로(최단/안전)로 안내를 시작하고 서버에 저장한다. 로그인한 경우만. */
    fun startGuidance() {
        val route = _uiState.value.route
        val result = route.displayedResult
        if (route.guidanceBusy || route.guidance != null) return
        if (result == null || result.segments.isEmpty()) {
            showMessage(R.string.route_guidance_need_route)
            return
        }
        if (!tokenStore.isLoggedIn()) {
            showMessage(R.string.route_guidance_need_login)
            return
        }
        if (result.segments.size >= MAX_ROUTE_POINTS) {
            showMessage(R.string.route_guidance_too_long)
            return
        }
        val startName = route.startName.ifBlank { "출발지" }
        val endName = route.endName.ifBlank { "도착지" }
        guidanceJob?.cancel()
        guidanceJob = viewModelScope.launch {
            setGuidanceBusy(true)
            // 안내가 시작되면 워치를 바꿀 수 없으므로, 붙어 있는 등록 워치를 먼저 연결(이미 연결 중이면 시각 갱신)한다.
            // 다른 워치로 바꿀지 묻는 화면은 프론트가 WatchConnectionManager.state를 보고 띄운다. 실패해도 안내는 시작한다
            WatchConnectionManager.refreshForGuidance(getApplication<Application>())
            runCatchingIo { guidanceRepository.start(result, startName, endName) }
                .onSuccess { started ->
                    _uiState.update {
                        it.copy(route = it.route.copy(
                            guidance = GuidanceState(started.routeId, startName, endName),
                            guidanceBusy = false,
                        ))
                    }
                    showMessage(R.string.route_guidance_started, startName, endName)
                    if (started.cancelledRouteId != null) showMessage(R.string.route_guidance_replaced)
                    // 안내 중에만 워치 이상 감지(낙상·심박) 팝업을 띄운다
                    GuidanceStore.set(getApplication<Application>(), started.routeId)
                }
                .onFailure { error ->
                    setGuidanceBusy(false)
                    showMessage(if ((error as? ApiClient.ApiException)?.statusCode == 401) {
                        R.string.route_guidance_need_login
                    } else {
                        R.string.route_guidance_failed
                    })
                }
        }
    }

    /** completed=true면 도착 완료, false면 안내 종료(취소) */
    fun finishGuidance(completed: Boolean) {
        val guidance = _uiState.value.route.guidance ?: return
        guidanceJob?.cancel()
        guidanceJob = viewModelScope.launch {
            setGuidanceBusy(true)
            runCatchingIo { guidanceRepository.finish(guidance.routeId, completed) }
                .onSuccess {
                    clearGuidance(guidance.routeId)
                    showMessage(if (completed) R.string.route_guidance_completed else R.string.route_guidance_cancelled)
                }
                .onFailure { error ->
                    when ((error as? ApiClient.ApiException)?.statusCode) {
                        // 이미 끝났거나(409, 예: 6시간 방치로 서버가 자동 취소) 없는 경로(404) → 화면에서도 정리
                        404, 409 -> {
                            clearGuidance(guidance.routeId)
                            showMessage(R.string.route_guidance_cancelled)
                        }
                        else -> {
                            setGuidanceBusy(false)
                            showMessage(R.string.route_guidance_failed)
                        }
                    }
                }
        }
    }

    /** 앱을 다시 켰을 때 서버에 진행 중 경로가 있으면 이어서 도착/종료할 수 있게 복원한다. */
    private fun restoreActiveGuidance() {
        if (!tokenStore.isLoggedIn()) return
        guidanceJob = viewModelScope.launch {
            runCatchingIo { guidanceRepository.active() }
                .onSuccess { active ->
                    if (active == null || _uiState.value.route.guidance != null) return@onSuccess
                    val startName = active.startName ?: "출발지"
                    val endName = active.endName ?: "도착지"
                    _uiState.update {
                        it.copy(route = it.route.copy(guidance = GuidanceState(active.routeId, startName, endName)))
                    }
                    GuidanceStore.set(getApplication<Application>(), active.routeId)
                    showMessage(R.string.route_guidance_restored, startName, endName)
                }
            // 복원 실패(오프라인 등)는 조용히 넘어간다. 서버가 6시간 뒤 자동 취소한다.
        }
    }

    private fun setGuidanceBusy(busy: Boolean) {
        _uiState.update { it.copy(route = it.route.copy(guidanceBusy = busy)) }
    }

    private fun clearGuidance(routeId: Long) {
        if (GuidanceStore.activeRouteId(getApplication<Application>()) == routeId) {
            GuidanceStore.clear(getApplication<Application>())
        }
        _uiState.update {
            if (it.route.guidance?.routeId == routeId) {
                it.copy(route = it.route.copy(guidance = null, guidanceBusy = false))
            } else {
                it.copy(route = it.route.copy(guidanceBusy = false))
            }
        }
    }

    private fun loadSafeMap(viewport: MapViewport) {
        if (!_uiState.value.safeMapEnabled || !repository.canLoadSafeMap) return
        safeMapJob?.cancel()
        safeMapJob = viewModelScope.launch {
            runCatchingIo { repository.loadSafeMap(viewport) }
                .onSuccess { emitEffect(MapEffect.SafeMapImageReady(it)) }
                .onFailure { showMessage(R.string.safemap_load_failed) }
        }
    }

    private fun loadFacilities(viewport: MapViewport) {
        if (!_uiState.value.facilitiesEnabled || !repository.canSearchPlaces) return
        facilityJob?.cancel()
        facilityJob = viewModelScope.launch {
            runCatchingIo { repository.loadFacilities(viewport.bounds) }
                .onSuccess { facilities ->
                    routeGraph = null
                    _uiState.update { it.copy(facilities = facilities) }
                }
        }
    }

    private fun loadCctvSites() {
        val state = _uiState.value
        if (!state.cctvEnabled || state.cctvSites.isNotEmpty() || cctvJob?.isActive == true) return
        cctvJob = viewModelScope.launch {
            runCatchingIo(repository::loadCctvSites)
                .onSuccess { sites ->
                    routeGraph = null
                    _uiState.update { it.copy(cctvSites = sites) }
                }
                .onFailure { showMessage(R.string.cctv_load_failed) }
        }
    }

    private fun loadSecurityLights() {
        val state = _uiState.value
        if (!state.securityLightsEnabled || state.securityLightSites.isNotEmpty() || securityLightJob?.isActive == true) return
        securityLightJob = viewModelScope.launch {
            runCatchingIo(repository::loadSecurityLightSites)
                .onSuccess { sites ->
                    routeGraph = null
                    _uiState.update { it.copy(securityLightSites = sites) }
                }
                .onFailure { showMessage(R.string.security_light_load_failed) }
        }
    }

    private suspend fun <T> runCatchingIo(block: () -> T): Result<T> = try {
        Result.success(withContext(Dispatchers.IO) { block() })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun showMessage(resourceId: Int, vararg arguments: Any) {
        emitEffect(MapEffect.ShowMessage(UiText(resourceId, arguments.toList())))
    }

    private fun emitEffect(effect: MapEffect) {
        _effects.trySend(effect)
    }

    private fun Place.toGeoPoint() = GeoPoint(latitude, longitude)

    private sealed interface RouteCalculation {
        data object OutOfArea : RouteCalculation
        data class Complete(
            val shortest: RouteAStar.RouteResult?,
            val safe: RouteAStar.RouteResult?,
        ) : RouteCalculation
    }
}
