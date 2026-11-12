package com.safewalk.watch

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.safewalk.auth.TokenStore
import com.safewalk.common.network.ApiClient
import com.safewalk.common.network.AuthenticatedApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** 워치가 PATH_INFO로 알려 준 자기 정보. deviceId = 워치 ANDROID_ID (서버 deviceIdentifier) */
data class WatchInfo(val nodeId: String, val deviceId: String, val model: String, val appVersion: String)

/** 서버에 등록된 내 기기 (GET /api/wearables 항목) */
data class RegisteredWearable(
    val wearableId: Long,
    val deviceIdentifier: String,
    val deviceName: String?,
    val isActive: Boolean,
)

/** 연결 API 409 data { reason, currentWearable } */
data class ConnectionConflict(val reason: String, val message: String, val current: RegisteredWearable?)

/**
 * 프론트(화면)가 보고 그리는 워치 연결 상태. 팝업·버튼 같은 UI는 프론트가 이 값을 보고 만든다.
 */
sealed interface WatchConnectionState {
    /** 아직 확인 전 (로그인 전 포함) */
    data object Unknown : WatchConnectionState

    /** 블루투스로 붙어 있는 (앱이 설치된) 워치가 없음 */
    data object NoWatch : WatchConnectionState

    /** 미등록 워치 발견 → 등록 유도. register()로 등록하면 바로 연결된다 */
    data class Unregistered(val watch: WatchInfo) : WatchConnectionState

    /** 등록된 워치와 연결 중 (서버 is_active=true) */
    data class Connected(val wearableId: Long, val deviceName: String?) : WatchConnectionState

    /** 등록된 워치가 붙어 있지만 사용자가 앱에서 연결을 해제함. 다시 연결하려면 connect() */
    data class Disconnected(val wearableId: Long) : WatchConnectionState

    /** 내 다른 워치가 연결 중 → 사용자에게 바꿀지 묻고, 바꾸면 connect(replaceCurrent = true) */
    data class SwitchRequired(val watch: WatchInfo, val wearableId: Long, val current: RegisteredWearable?) :
        WatchConnectionState

    /** 연결할 수 없음. reason: GUIDANCE_IN_PROGRESS(안내 중 전환 불가) / IN_USE_BY_OTHER_ACCOUNT / ERROR */
    data class Blocked(val reason: String, val message: String) : WatchConnectionState
}

/** 한 번만 보여 줄 알림성 이벤트 (프론트가 팝업으로 띄울 때 사용) */
sealed interface WatchConnectionEvent {
    /** 연결 중이던 워치의 블루투스 연결이 끊겨 앱 연결도 해제됨 */
    data object WatchDisconnected : WatchConnectionEvent
}

fun parseWatchInfo(nodeId: String, json: JSONObject): WatchInfo? {
    val deviceId = json.optString("deviceId").trim()
    if (deviceId.isEmpty()) return null
    return WatchInfo(nodeId, deviceId, json.optString("model"), json.optString("appVersion"))
}

fun parseRegisteredWearable(json: JSONObject): RegisteredWearable = RegisteredWearable(
    wearableId = json.optLong("wearableId"),
    deviceIdentifier = json.optString("deviceIdentifier"),
    deviceName = if (json.isNull("deviceName")) null else json.optString("deviceName").takeIf { it.isNotBlank() },
    isActive = json.optBoolean("isActive"),
)

fun parseRegisteredWearables(array: JSONArray): List<RegisteredWearable> =
    (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseRegisteredWearable) }

/** 409 응답 data → 충돌 정보. 연결 규칙 충돌이 아니면(reason 없음) null */
fun parseConnectionConflict(data: Any?, message: String): ConnectionConflict? {
    val json = data as? JSONObject ?: return null
    val reason = json.optString("reason").takeIf { it.isNotBlank() } ?: return null
    val current = json.optJSONObject("currentWearable")?.let(::parseRegisteredWearable)
    return ConnectionConflict(reason, message, current)
}

/**
 * 폰 앱 ↔ 워치 ↔ 서버 연결 관리 (Galaxy Watch, WEAR_OS). 규칙은 백엔드 WearableService와 같다.
 *
 *  - 등록: 회원가입 후(선택)·마이페이지 등에서 프론트가 [register] 호출 → PUT /api/wearables 후 바로 연결
 *  - 연결: 서버 is_active는 실제 블루투스 연결을 따른다. 사용자가 고르는 "연결 안 함"은 없다
 *      앱 실행([syncOnLaunch]) / 경로 안내 시작([refreshForGuidance]) / 등록된 워치가 다시 붙을 때 자동 연결.
 *      내 다른 워치가 연결 중이면 [WatchConnectionState.SwitchRequired] → 프론트가 묻고 connect(replaceCurrent = true)
 *  - 해제: 블루투스 연결이 끊기면 자동 해제 + "워치 연결이 해제되었어요" 알림 ([onReachableWatchesChanged]).
 *      사용자가 앱에서 [disconnect]하면 그 워치는 블루투스가 한 번 끊겼다 다시 붙거나 [connect]를 부를 때까지
 *      자동으로 다시 연결하지 않는다
 *      앱이 강제 종료돼 해제를 못 보냈으면 다음 실행 때 [syncOnLaunch]가 실제 연결과 비교해 정리한다
 *  - 미등록 워치: 센서 데이터(경보·심박·이벤트·센서 배치)를 받지 않고 등록 유도 알림만 띄운다 ([acceptsData])
 *
 * 워치 정보(ANDROID_ID)는 워치에 PATH_INFO_REQUEST를 보내 PATH_INFO로 받는다 (최대 5초).
 * WearableListenerService 스레드에서 워치 응답을 기다리면 응답 메시지가 같은 스레드로 와서 막히므로,
 * 응답을 기다리는 작업은 항상 이 객체의 [scope]에서 한다.
 */
object WatchConnectionManager {
    private const val TAG = "WatchConnection"
    private const val PREFS = "safewalk_watch_connection"
    private const val INFO_TIMEOUT_MS = 5_000L
    private const val PROMPT_INTERVAL_MS = 6 * 60 * 60 * 1000L        // 같은 워치 등록 유도 알림은 6시간에 1번
    private const val REFRESH_INTERVAL_MS = 60 * 1000L                // 미등록 판정 시 서버 목록 재확인 간격
    private const val AUTO_CONNECT_INTERVAL_MS = 60 * 1000L           // 데이터 수신 중 자동 연결 재시도 간격

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingInfo = ConcurrentHashMap<String, PendingInfo>()

    /** 워치 정보 응답 대기 (minSdk 23이라 CompletableFuture 대신) */
    private class PendingInfo {
        val latch = CountDownLatch(1)
        val info = AtomicReference<WatchInfo?>(null)
        fun complete(value: WatchInfo) {
            info.set(value)
            latch.countDown()
        }
    }
    @Volatile private var lastRefreshMs = 0L
    private val lastAutoConnectMs = ConcurrentHashMap<String, Long>()

    private val _state = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Unknown)
    val state: StateFlow<WatchConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WatchConnectionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WatchConnectionEvent> = _events.asSharedFlow()

    // ── 프론트에서 부르는 함수 (suspend, IO에서 실행) ─────────────────────────

    /** 앱 실행 시(지도 화면): 서버의 연결 상태를 실제 블루투스 연결과 맞춘다 */
    fun syncOnLaunch(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { sync(app) }.onFailure { Log.w(TAG, "연결 확인 실패: ${it.message}") } }
    }

    /** 경로 안내 시작 직전: 붙어 있는 등록 워치를 연결(이미 연결 중이면 시각 갱신). 실패해도 안내는 진행 */
    suspend fun refreshForGuidance(context: Context) = withContext(Dispatchers.IO) {
        runCatching { sync(context.applicationContext) }.onFailure { Log.w(TAG, "안내 전 연결 확인 실패: ${it.message}") }
        Unit
    }

    /** 워치 등록 (회원가입 후·마이페이지). 붙어 있는 워치를 찾아 등록하고 바로 연결한다 */
    suspend fun register(context: Context): WatchConnectionState = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val watch = discover(app) ?: return@withContext publish(WatchConnectionState.NoWatch)
        setManualOff(app, watch.nodeId, null)
        val body = JSONObject()
            .put("deviceType", "WEAR_OS")
            .put("deviceIdentifier", watch.deviceId)
        if (watch.model.isNotBlank()) body.put("deviceName", watch.model)
        val registered = try {
            parseRegisteredWearable(AuthenticatedApiClient(app).putObject("/api/wearables", body))
        } catch (error: ApiClient.ApiException) {
            return@withContext publish(WatchConnectionState.Blocked("ERROR", error.message ?: "워치 등록에 실패했어요."))
        }
        rememberRegistered(app, registered)
        WatchConnectionNotifier.cancelRegisterPrompt(app)
        connectRegistered(app, registered.wearableId, watch, replaceCurrent = false)
    }

    /** 연결 (SwitchRequired에서 사용자가 "바꾸기"를 고르면 replaceCurrent = true) */
    suspend fun connect(context: Context, replaceCurrent: Boolean = false): WatchConnectionState =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val watch = discover(app) ?: return@withContext publish(WatchConnectionState.NoWatch)
            setManualOff(app, watch.nodeId, null)
            val registered = findRegistered(app, watch.deviceId, refresh = true)
                ?: return@withContext unregistered(app, watch)
            connectRegistered(app, registered.wearableId, watch, replaceCurrent)
        }

    /** 앱에서 연결 해제 (워치가 꺼져 있어도 서버 상태만 바꾸면 된다) */
    suspend fun disconnect(context: Context): WatchConnectionState = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val activeId = activeWearableId(app) ?: return@withContext publish(WatchConnectionState.NoWatch)
        val activeNode = prefs(app).getString(activeNodeKey(app), null)
        disconnectOnServer(app, activeId)
        if (activeNode != null) setManualOff(app, activeNode, activeId)
        publish(WatchConnectionState.Disconnected(activeId))
    }

    /** 로그아웃 직전: 연결을 해제해 두어야 다른 계정이 같은 워치를 바로 연결할 수 있다 */
    suspend fun onLogout(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        activeWearableId(app)?.let { runCatching { disconnectOnServer(app, it) } }
        _state.value = WatchConnectionState.Unknown
    }

    // ── WatchMessageService에서 부르는 함수 ────────────────────────────────

    /** 워치가 보낸 PATH_INFO 응답 */
    fun onWatchInfo(context: Context, nodeId: String, data: ByteArray) {
        val info = runCatching { parseWatchInfo(nodeId, JSONObject(String(data))) }.getOrNull() ?: return
        prefs(context).edit().putString(nodeKey(nodeId), info.deviceId).apply()
        pendingInfo.remove(nodeId)?.complete(info)
    }

    /**
     * 워치 → 폰 데이터를 받아도 되는지. 등록된 워치에서 온 것만 true.
     * 미등록 워치면 false + 등록 유도 알림. 등록은 됐는데 앱 연결이 안 된 상태면 자동으로 연결을 시도한다.
     * (메시지 수신 스레드를 막지 않는다)
     */
    fun acceptsData(context: Context, nodeId: String, deviceIdFromPayload: String?): Boolean {
        val app = context.applicationContext
        if (!TokenStore(app).isLoggedIn()) {
            maybePrompt(app, "login") { WatchConnectionNotifier.showRegisterPrompt(app, loggedIn = false) }
            return false
        }
        val deviceId = deviceIdFromPayload?.takeIf { it.isNotBlank() }
            ?: prefs(app).getString(nodeKey(nodeId), null)
        if (deviceId == null) {
            // 처음 보는 워치: 정보를 물어보고(비동기) 이번 데이터는 버린다
            scope.launch { runCatching { requestInfo(app, nodeId) } }
            return false
        }
        prefs(app).edit().putString(nodeKey(nodeId), deviceId).apply()
        val wearableId = cachedWearableId(app, deviceId)
        if (wearableId == null) {
            maybePromptRegister(app, deviceId)
            refreshRegisteredAsync(app)
            return false
        }
        val now = System.currentTimeMillis()
        if (activeWearableId(app) != wearableId && !isManualOff(app, nodeId) &&
            now - (lastAutoConnectMs[nodeId] ?: 0L) >= AUTO_CONNECT_INTERVAL_MS
        ) {
            lastAutoConnectMs[nodeId] = now
            scope.launch {
                runCatching { connectRegistered(app, wearableId, WatchInfo(nodeId, deviceId, "", ""), false) }
            }
        }
        return true
    }

    /** 블루투스로 붙어 있는 워치(capability safewalk_watch) 목록이 바뀜 */
    fun onReachableWatchesChanged(context: Context, reachableNodeIds: Set<String>) {
        val app = context.applicationContext
        if (!TokenStore(app).isLoggedIn()) return
        scope.launch {
            runCatching {
                // 블루투스가 끊긴 워치는 "앱에서 해제함" 표시를 지운다 (다시 붙으면 자동 연결)
                clearManualOffExcept(app, reachableNodeIds)
                val activeId = activeWearableId(app)
                val activeNode = prefs(app).getString(activeNodeKey(app), null)
                if (activeId != null && activeNode != null && activeNode !in reachableNodeIds) {
                    // 연결 중이던 워치가 끊김 → 앱 연결 해제 + 알림
                    disconnectOnServer(app, activeId)
                    publish(WatchConnectionState.NoWatch)
                    WatchConnectionNotifier.showDisconnected(app)
                    _events.tryEmit(WatchConnectionEvent.WatchDisconnected)
                } else if (activeId == null && reachableNodeIds.isNotEmpty()) {
                    // 워치가 다시 붙음 → 등록된 워치면 자동 연결, 아니면 등록 유도
                    sync(app)
                }
            }.onFailure { Log.w(TAG, "연결 변화 처리 실패: ${it.message}") }
        }
    }

    // ── 내부 ──────────────────────────────────────────────────────────

    /** 서버 상태를 실제 연결과 맞춘다: 끊겼으면 해제, 붙어 있으면 연결(갱신), 미등록이면 등록 유도 */
    @WorkerThread
    private fun sync(app: Context): WatchConnectionState {
        if (!TokenStore(app).isLoggedIn()) return publish(WatchConnectionState.Unknown)
        val registered = refreshRegistered(app)
        val active = registered.firstOrNull { it.isActive }
        // 워치가 여러 대 붙어 있으면 연결 중이던 워치를 우선 확인한다
        val watch = discover(app, preferredNodeId = prefs(app).getString(activeNodeKey(app), null))
        if (active != null && active.deviceIdentifier != watch?.deviceId) {
            // 강제 종료 등으로 남은 연결: 지금 붙어 있는 워치가 아니면 해제
            disconnectOnServer(app, active.wearableId)
        }
        if (watch == null) return publish(WatchConnectionState.NoWatch)
        val mine = registered.firstOrNull { it.deviceIdentifier == watch.deviceId } ?: return unregistered(app, watch)
        if (isManualOff(app, watch.nodeId)) return publish(WatchConnectionState.Disconnected(mine.wearableId))
        return connectRegistered(app, mine.wearableId, watch, replaceCurrent = false)
    }

    @WorkerThread
    private fun connectRegistered(app: Context, wearableId: Long, watch: WatchInfo, replaceCurrent: Boolean): WatchConnectionState {
        val body = JSONObject().put("connected", true).put("replaceCurrent", replaceCurrent)
        return try {
            val data = AuthenticatedApiClient(app).patchObject("/api/wearables/$wearableId/connection", body)
            val wearable = data.optJSONObject("wearable")?.let(::parseRegisteredWearable)
            prefs(app).edit()
                .putLong(activeKey(app), wearableId)
                .putString(activeNodeKey(app), watch.nodeId)
                .apply()
            publish(WatchConnectionState.Connected(wearableId, wearable?.deviceName))
        } catch (error: ApiClient.ApiException) {
            val conflict = if (error.statusCode == 409) parseConnectionConflict(error.responseData, error.message ?: "") else null
            when {
                conflict != null && conflict.reason == "SWITCH_REQUIRED" ->
                    publish(WatchConnectionState.SwitchRequired(watch, wearableId, conflict.current))
                conflict != null -> publish(WatchConnectionState.Blocked(conflict.reason, conflict.message))
                error.statusCode == 404 -> {
                    // 다른 기기에서 삭제됨 → 캐시를 비우고 미등록으로
                    forgetRegistered(app, watch.deviceId)
                    unregistered(app, watch)
                }
                else -> publish(WatchConnectionState.Blocked("ERROR", error.message ?: "워치 연결에 실패했어요."))
            }
        }
    }

    @WorkerThread
    private fun disconnectOnServer(app: Context, wearableId: Long) {
        try {
            AuthenticatedApiClient(app).patchObject(
                "/api/wearables/$wearableId/connection", JSONObject().put("connected", false),
            )
        } catch (error: ApiClient.ApiException) {
            if (error.statusCode != 404) throw error
        }
        prefs(app).edit().remove(activeKey(app)).remove(activeNodeKey(app)).apply()
    }

    private fun unregistered(app: Context, watch: WatchInfo): WatchConnectionState {
        maybePromptRegister(app, watch.deviceId)
        return publish(WatchConnectionState.Unregistered(watch))
    }

    /** 붙어 있는 워치를 찾아 정보를 받는다. 없거나 5초 안에 답이 없으면 null */
    @WorkerThread
    private fun discover(app: Context, preferredNodeId: String? = null): WatchInfo? {
        val nodes = runCatching {
            Tasks.await(
                Wearable.getCapabilityClient(app).getCapability(Protocol.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE),
                INFO_TIMEOUT_MS, TimeUnit.MILLISECONDS,
            ).nodes
        }.getOrNull().orEmpty()
        val node = nodes.firstOrNull { it.id == preferredNodeId }
            ?: nodes.firstOrNull { it.isNearby }
            ?: nodes.firstOrNull()
            ?: return null
        return requestInfo(app, node.id)
    }

    @WorkerThread
    private fun requestInfo(app: Context, nodeId: String): WatchInfo? {
        val fresh = PendingInfo()
        val pending = pendingInfo.putIfAbsent(nodeId, fresh) ?: fresh
        return try {
            Tasks.await(
                Wearable.getMessageClient(app).sendMessage(nodeId, Protocol.PATH_INFO_REQUEST, ByteArray(0)),
                INFO_TIMEOUT_MS, TimeUnit.MILLISECONDS,
            )
            if (pending.latch.await(INFO_TIMEOUT_MS, TimeUnit.MILLISECONDS)) pending.info.get() else null
        } catch (error: Exception) {
            Log.w(TAG, "워치 정보 요청 실패: ${error.message}")
            null
        } finally {
            pendingInfo.remove(nodeId, pending)
        }
    }

    // ── 등록 기기 캐시 (계정별) ─────────────────────────────────────────

    @WorkerThread
    private fun refreshRegistered(app: Context): List<RegisteredWearable> {
        val list = parseRegisteredWearables(AuthenticatedApiClient(app).getArray("/api/wearables"))
        val editor = prefs(app).edit()
        prefs(app).all.keys.filter { it.startsWith(registeredPrefix(app)) }.forEach { editor.remove(it) }
        list.forEach { editor.putLong(registeredPrefix(app) + it.deviceIdentifier, it.wearableId) }
        val active = list.firstOrNull { it.isActive }
        if (active == null) editor.remove(activeKey(app)) else editor.putLong(activeKey(app), active.wearableId)
        editor.apply()
        lastRefreshMs = System.currentTimeMillis()
        return list
    }

    private fun refreshRegisteredAsync(app: Context) {
        if (System.currentTimeMillis() - lastRefreshMs < REFRESH_INTERVAL_MS) return
        lastRefreshMs = System.currentTimeMillis()
        scope.launch { runCatching { refreshRegistered(app) } }
    }

    @WorkerThread
    private fun findRegistered(app: Context, deviceId: String, refresh: Boolean): RegisteredWearable? {
        if (refresh) return refreshRegistered(app).firstOrNull { it.deviceIdentifier == deviceId }
        return cachedWearableId(app, deviceId)?.let { RegisteredWearable(it, deviceId, null, false) }
    }

    private fun rememberRegistered(app: Context, wearable: RegisteredWearable) {
        prefs(app).edit().putLong(registeredPrefix(app) + wearable.deviceIdentifier, wearable.wearableId).apply()
    }

    private fun forgetRegistered(app: Context, deviceId: String) {
        prefs(app).edit().remove(registeredPrefix(app) + deviceId).apply()
    }

    private fun cachedWearableId(app: Context, deviceId: String): Long? =
        prefs(app).getLong(registeredPrefix(app) + deviceId, 0L).takeIf { it > 0L }

    private fun activeWearableId(app: Context): Long? =
        prefs(app).getLong(activeKey(app), 0L).takeIf { it > 0L }

    private fun maybePromptRegister(app: Context, deviceId: String) =
        maybePrompt(app, deviceId) { WatchConnectionNotifier.showRegisterPrompt(app, loggedIn = true) }

    /** 같은 알림이 데이터마다 뜨지 않게 [PROMPT_INTERVAL_MS]에 한 번만 */
    private inline fun maybePrompt(app: Context, key: String, show: () -> Unit) {
        val prefKey = "prompt_at_$key"
        val now = System.currentTimeMillis()
        if (now - prefs(app).getLong(prefKey, 0L) < PROMPT_INTERVAL_MS) return
        prefs(app).edit().putLong(prefKey, now).apply()
        show()
    }

    /** 사용자가 앱에서 해제한 워치(노드) 표시. wearableId = null이면 표시를 지운다 */
    private fun setManualOff(app: Context, nodeId: String, wearableId: Long?) {
        val editor = prefs(app).edit()
        if (wearableId == null) editor.remove(manualOffKey(app, nodeId)) else editor.putLong(manualOffKey(app, nodeId), wearableId)
        editor.apply()
    }

    private fun isManualOff(app: Context, nodeId: String): Boolean = prefs(app).contains(manualOffKey(app, nodeId))

    private fun clearManualOffExcept(app: Context, reachableNodeIds: Set<String>) {
        val prefix = manualOffKey(app, "")
        val stale = prefs(app).all.keys.filter { it.startsWith(prefix) && it.removePrefix(prefix) !in reachableNodeIds }
        if (stale.isEmpty()) return
        val editor = prefs(app).edit()
        stale.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun publish(state: WatchConnectionState): WatchConnectionState {
        _state.value = state
        return state
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun userKey(app: Context) = TokenStore(app).userId() ?: 0L
    private fun registeredPrefix(app: Context) = "registered_${userKey(app)}_"
    private fun activeKey(app: Context) = "active_wearable_${userKey(app)}"
    private fun activeNodeKey(app: Context) = "active_node_${userKey(app)}"
    private fun manualOffKey(app: Context, nodeId: String) = "manual_off_${userKey(app)}_$nodeId"
    private fun nodeKey(nodeId: String) = "node_$nodeId"
}
