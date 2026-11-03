package com.safewalk.emergency

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.safewalk.BuildConfig
import com.safewalk.R
import com.safewalk.common.network.ApiClient
import com.safewalk.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class EmergencyMapActivity : ComponentActivity(), EmergencySseClient.Listener {
    private var mapView: MapView? = null
    private var kakaoMap: KakaoMap? = null
    private var marker: Label? = null
    private var pendingLocation: EmergencyLocation? = null
    private var cameraCentered = false
    private var sseClient: EmergencySseClient? = null
    private var detailLoadGeneration = 0
    private var viewerRole: String? = null
    private var ended = false
    private val repository by lazy { EmergencyRepository(this) }
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var status: TextView
    private lateinit var resolveButton: Button
    private val emergencyId: Long by lazy {
        intent.getLongExtra(EXTRA_EMERGENCY_ID, 0L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (emergencyId <= 0L) {
            finish()
            return
        }
        setContentView(R.layout.activity_emergency_map)
        configureInsets()
        title = findViewById(R.id.emergency_map_title)
        subtitle = findViewById(R.id.emergency_map_subtitle)
        status = findViewById(R.id.emergency_map_status)
        resolveButton = findViewById(R.id.emergency_resolve_button)
        resolveButton.isEnabled = false
        intent.getStringExtra(EXTRA_WARD_NAME)?.takeIf { it.isNotBlank() }?.let {
            title.text = "$it 님의 SOS 위치"
        }
        resolveButton.setOnClickListener {
            if (viewerRole == "SELF") chooseOwnEndStatus() else confirmGuardianResolve()
        }
        findViewById<Button>(R.id.emergency_map_back).setOnClickListener { finish() }
        startMap()
    }

    override fun onStart() {
        super.onStart()
        if (!::resolveButton.isInitialized) return
        if (emergencyId > 0L && !ended) loadDetail(++detailLoadGeneration)
    }

    override fun onStop() {
        detailLoadGeneration++
        sseClient?.stop()
        sseClient = null
        super.onStop()
    }

    private fun configureInsets() {
        val root = findViewById<View>(R.id.emergency_map_root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
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
        root.requestApplyInsets()
    }

    private fun loadDetail(generation: Int) {
        lifecycleScope.launch {
            try {
                val (detail, latest) = withContext(Dispatchers.IO) {
                    val detail = repository.detail(emergencyId)
                    val latest = if (detail.status == "OCCURRED" && detail.canViewLocation) {
                        runCatching { repository.latestLocation(emergencyId) }.getOrNull()
                    } else {
                        null
                    }
                    detail to latest
                }
                if (generation != detailLoadGeneration) return@launch
                viewerRole = detail.viewer
                if (detail.viewer == "SELF") {
                    title.text = "내 SOS 위치 공유"
                    subtitle.text = "보호자에게 공유되는 내 최신 위치입니다."
                    resolveButton.text = "SOS 종료"
                } else {
                    title.text = "${detail.userName} 님의 SOS 위치"
                    subtitle.text = "보호 대상자의 최신 위치입니다."
                    resolveButton.text = "상황 종료 처리"
                }
                if (!ended && detail.status == "OCCURRED") {
                    resolveButton.isEnabled = true
                    if (!detail.canViewLocation) {
                        status.text = "위치 공유 동의가 없어 위치를 표시할 수 없습니다."
                    } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        sseClient = EmergencySseClient(this@EmergencyMapActivity, emergencyId, this@EmergencyMapActivity)
                            .also { it.start() }
                    }
                }
                if (detail.status != "OCCURRED") renderEnded(detail.status)
                if (pendingLocation == null) (latest ?: detail.initialLocation)?.let(::renderLocation)
            } catch (error: ApiClient.ApiException) {
                if (generation != detailLoadGeneration) return@launch
                if (error.statusCode == 401) openLogin() else {
                    resolveButton.isEnabled = false
                    status.text = error.message
                    sseClient?.stop()
                }
            } catch (error: Exception) {
                if (generation != detailLoadGeneration) return@launch
                resolveButton.isEnabled = false
                status.text = error.message ?: "SOS 정보를 불러오지 못했습니다."
            }
        }
    }

    private fun startMap() {
        val loading = findViewById<TextView>(R.id.emergency_map_loading)
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
            loading.text = "지도 설정이 필요합니다. Native App Key를 확인해 주세요."
            return
        }
        val view = MapView(this)
        findViewById<FrameLayout>(R.id.emergency_map_container)
            .addView(view, 0, FrameLayout.LayoutParams(-1, -1))
        mapView = view
        view.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() = Unit

            override fun onMapError(error: Exception) {
                runOnUiThread {
                    loading.visibility = View.VISIBLE
                    loading.text = "지도를 불러오지 못했습니다. 네트워크와 지도 키를 확인해 주세요."
                }
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(kakaoMap: KakaoMap) {
                runOnUiThread {
                    this@EmergencyMapActivity.kakaoMap = kakaoMap
                    loading.visibility = View.GONE
                    pendingLocation?.let(::renderLocation)
                }
            }

            override fun getPosition(): LatLng = LatLng.from(36.8100, 127.1467)
            override fun getZoomLevel(): Int = 16
        })
    }

    private fun renderLocation(location: EmergencyLocation) {
        if (isFinishing || isDestroyed) return
        val map = kakaoMap
        if (map == null) {
            pendingLocation = location
            return
        }
        val position = LatLng.from(location.latitude, location.longitude)
        val existing = marker
        if (existing == null) {
            val style = LabelStyle.from(this, R.drawable.marker_sos).setAnchorPoint(0.5f, 1f)
            marker = map.labelManager?.layer?.addLabel(
                LabelOptions.from("emergency-$emergencyId", position).setStyles(style),
            )
        } else {
            existing.moveTo(position, 800)
        }
        if (!cameraCentered) {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(position, 17))
            cameraCentered = true
        }
        val accuracy = location.accuracyMeters?.let { " · 오차 약 ${it.toInt()}m" }.orEmpty()
        if (!ended) {
            val prefix = if (viewerRole == "SELF") "보호자에게 공유된 위치" else "실시간 위치 수신 중"
            status.text = "$prefix$accuracy${formatRecordedAt(location.recordedAt)}"
        }
        pendingLocation = location
    }

    private fun chooseOwnEndStatus() {
        val labels = arrayOf("상황 해결", "오작동", "SOS 취소")
        val statuses = arrayOf("RESOLVED", "FALSE_ALARM", "CANCELLED")
        AlertDialog.Builder(this)
            .setTitle("SOS 종료 사유를 선택해 주세요")
            .setItems(labels) { _, index -> confirmResolve(statuses[index], labels[index]) }
            .setNegativeButton("계속 공유", null)
            .show()
    }

    private fun confirmGuardianResolve() = confirmResolve("RESOLVED", "상황 종료")

    private fun confirmResolve(endedStatus: String, label: String) {
        AlertDialog.Builder(this)
            .setTitle("SOS 상황을 종료할까요?")
            .setMessage("$label 처리하면 위치 공유와 실시간 연결이 중단됩니다.")
            .setPositiveButton("종료 확인") { _, _ -> resolve(endedStatus) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun resolve(endedStatus: String) {
        resolveButton.isEnabled = false
        status.text = "SOS를 종료하는 중…"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.resolve(emergencyId, endedStatus) }
                sseClient?.stop()
                renderEnded(endedStatus)
            } catch (error: ApiClient.ApiException) {
                if (!ended) resolveButton.isEnabled = true
                if (error.statusCode == 401) openLogin() else status.text = error.message
            } catch (error: Exception) {
                if (!ended) resolveButton.isEnabled = true
                status.text = error.message ?: "SOS를 종료하지 못했습니다."
            }
        }
    }

    private fun renderEnded(endedStatus: String) {
        ended = true
        resolveButton.isEnabled = false
        resolveButton.text = "종료된 SOS"
        status.text = "SOS가 종료되었습니다. (${statusLabel(endedStatus)})"
        sseClient?.stop()
        if (EmergencySessionStore(this).activeEmergencyId() == emergencyId) {
            EmergencyLocationService.stop(this, emergencyId)
        }
    }

    override fun onConnected() = runOnUiThread {
        if (!ended) status.text = "실시간 위치 연결됨 · 새 위치를 기다리는 중…"
    }

    override fun onLocation(location: EmergencyLocation) = runOnUiThread { renderLocation(location) }

    override fun onLocationStale(lastRecordedAt: String) = runOnUiThread {
        if (!ended) status.text = "위치 수신이 지연되고 있습니다. 마지막 위치${formatRecordedAt(lastRecordedAt)}"
    }

    override fun onStatus(status: String, resolvedAt: String) = runOnUiThread { renderEnded(status) }

    override fun onConnectionError(message: String, retrying: Boolean) = runOnUiThread {
        if (!ended) status.text = if (retrying) "$message 자동으로 다시 연결합니다." else message
    }

    override fun onAuthenticationExpired() = runOnUiThread { openLogin() }

    private fun openLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra("type", "EMERGENCY_STARTED")
                .putExtra(EXTRA_EMERGENCY_ID, emergencyId.toString())
                .putExtra(EXTRA_WARD_NAME, title.text.toString())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }

    private fun formatRecordedAt(raw: String): String = raw
        .takeIf { it.isNotBlank() }
        ?.replace('T', ' ')
        ?.take(16)
        ?.let { " · $it" }
        .orEmpty()

    private fun statusLabel(value: String) = when (value.uppercase(Locale.ROOT)) {
        "FALSE_ALARM" -> "오작동"
        "CANCELLED" -> "취소"
        else -> "상황 종료"
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
        marker = null
        kakaoMap = null
        mapView?.finish()
        mapView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_EMERGENCY_ID = "emergencyId"
        const val EXTRA_WARD_NAME = "wardName"
    }
}
