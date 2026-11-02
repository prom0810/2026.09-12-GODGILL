package com.safewalk.report.view

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.safewalk.BuildConfig
import com.safewalk.R
import com.safewalk.common.network.ApiClient
import com.safewalk.main.navigation.BaseMainTabActivity
import com.safewalk.main.navigation.MainTab
import com.safewalk.report.ReportImageLoader
import com.safewalk.report.RiskReportItem
import com.safewalk.report.RiskReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportDetailActivity : BaseMainTabActivity() {
    override val layoutResource: Int = R.layout.activity_report_detail
    override val selectedTab: MainTab = MainTab.REPORT
    private var mapView: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<Button>(R.id.report_detail_back).setOnClickListener { finish() }
        val reportId = intent.getLongExtra(EXTRA_REPORT_ID, -1L)
        if (reportId <= 0L) {
            showError("제보 번호를 확인할 수 없습니다.")
            return
        }
        loadDetail(reportId)
    }

    private fun loadDetail(reportId: Long) {
        lifecycleScope.launch {
            try {
                val item = withContext(Dispatchers.IO) {
                    RiskReportRepository(this@ReportDetailActivity).detail(reportId)
                }
                render(item)
            } catch (error: Exception) {
                val message = if (error is ApiClient.ApiException && error.statusCode == 404) {
                    "제보가 삭제되었거나 확인할 수 없는 상태입니다."
                } else {
                    error.message ?: "제보 상세 내용을 불러오지 못했습니다."
                }
                showError(message)
            }
        }
    }

    private fun render(item: RiskReportItem) {
        findViewById<TextView>(R.id.report_detail_loading).visibility = View.GONE
        findViewById<View>(R.id.report_detail_content).visibility = View.VISIBLE
        findViewById<TextView>(R.id.report_detail_title).text = item.riskTypeLabel
        val author = if (item.mine) "내 제보" else item.authorName
        findViewById<TextView>(R.id.report_detail_meta).text = "$author  ·  ${formatTime(item.createdAt)}"
        findViewById<TextView>(R.id.report_detail_state).text = statusLabel(item.status)
        findViewById<TextView>(R.id.report_detail_description).text =
            item.description.ifBlank { "작성된 상세 내용이 없습니다." }
        findViewById<TextView>(R.id.report_detail_location).text =
            item.address.ifBlank { "지도에 표시된 위치" }
        startMap(item)

        val container = findViewById<LinearLayout>(R.id.report_detail_images)
        container.removeAllViews()
        if (item.images.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "첨부된 사진이 없습니다."
                textSize = 13f
                setTextColor(Color.parseColor("#737A77"))
                setPadding(0, dp(12), 0, 0)
            })
            return
        }
        item.images.forEachIndexed { index, image ->
            val imageView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#E9EEEB"))
                contentDescription = "${item.riskTypeLabel} 제보 사진 ${index + 1}"
                layoutParams = LinearLayout.LayoutParams(-1, dp(220)).apply { topMargin = dp(12) }
            }
            container.addView(imageView)
            lifecycleScope.launch {
                val bitmap = ReportImageLoader.load(image.url, maxWidth = 1200, maxHeight = 900)
                if (bitmap != null && imageView.isAttachedToWindow) imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun showError(message: String) {
        findViewById<View>(R.id.report_detail_content).visibility = View.GONE
        findViewById<TextView>(R.id.report_detail_loading).apply {
            visibility = View.VISIBLE
            text = message
        }
    }

    private fun startMap(item: RiskReportItem) {
        val status = findViewById<TextView>(R.id.report_detail_map_status)
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
            status.text = "지도 설정이 필요합니다. Native App Key를 확인해 주세요."
            return
        }
        val position = LatLng.from(item.latitude, item.longitude)
        val container = findViewById<FrameLayout>(R.id.report_detail_map)
        val view = MapView(this)
        container.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
        mapView = view
        view.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() = Unit

            override fun onMapError(error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        status.visibility = View.VISIBLE
                        status.text = "지도를 불러오지 못했습니다. 인터넷 연결을 확인해 주세요."
                    }
                }
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(kakaoMap: KakaoMap) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    disableGestures(kakaoMap)
                    val style = LabelStyle.from(this@ReportDetailActivity, R.drawable.marker_report)
                        .setAnchorPoint(0.5f, 1f)
                    val labelLayer = kakaoMap.labelManager?.layer
                    if (labelLayer == null) {
                        status.visibility = View.VISIBLE
                        status.text = "제보 위치 마커를 표시하지 못했습니다."
                        return@runOnUiThread
                    }
                    labelLayer.addLabel(
                        LabelOptions.from("report-${item.reportId}", position).setStyles(style),
                    )
                    status.visibility = View.GONE
                }
            }

            override fun getPosition(): LatLng = position
            override fun getZoomLevel(): Int = 16
        })
    }

    private fun disableGestures(map: KakaoMap) {
        listOf(
            GestureType.OneFingerDoubleTap,
            GestureType.TwoFingerSingleTap,
            GestureType.Pan,
            GestureType.Rotate,
            GestureType.Zoom,
            GestureType.Tilt,
            GestureType.LongTapAndDrag,
            GestureType.RotateZoom,
            GestureType.OneFingerZoom,
        ).forEach { map.setGestureEnable(it, false) }
    }

    override fun onDestroy() {
        mapView?.finish()
        mapView = null
        super.onDestroy()
    }

    private fun statusLabel(status: String): String = when (status) {
        "APPROVED" -> "지도에 반영된 제보"
        "REJECTED" -> "반려된 제보"
        else -> "확인 중인 제보"
    }

    private fun formatTime(raw: String): String = raw
        .replace('T', ' ')
        .take(16)
        .ifBlank { "시간 정보 없음" }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_REPORT_ID = "report_id"

        fun intent(context: Context, reportId: Long): Intent =
            Intent(context, ReportDetailActivity::class.java).putExtra(EXTRA_REPORT_ID, reportId)
    }
}
