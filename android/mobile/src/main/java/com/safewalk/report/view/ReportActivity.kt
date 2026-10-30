package com.safewalk.report.view

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.safewalk.R
import com.safewalk.auth.TokenStore
import com.safewalk.common.network.ApiClient
import com.safewalk.login.LoginActivity
import com.safewalk.main.navigation.BaseMainTabActivity
import com.safewalk.main.navigation.MainTab
import com.safewalk.report.RiskReportItem
import com.safewalk.report.ReportImageLoader
import com.safewalk.report.RiskReportRepository
import com.safewalk.watch.PhoneLocation
import com.safewalk.watch.WatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportActivity : BaseMainTabActivity() {
    override val layoutResource: Int = R.layout.activity_report
    override val selectedTab: MainTab = MainTab.REPORT

    private lateinit var repository: RiskReportRepository
    private lateinit var reportList: LinearLayout
    private lateinit var feedStatus: TextView
    private lateinit var loadMore: Button
    private lateinit var feedSection: View
    private lateinit var formSection: View
    private var selectedImages: List<Uri> = emptyList()
    private var nextPage = 0
    private var loading = false
    private var showingFeed = true

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImages = uris.distinct().take(MAX_IMAGES)
        findViewById<TextView>(R.id.report_images_status).text =
            if (selectedImages.isEmpty()) "선택된 사진 없음" else "사진 ${selectedImages.size}장 선택됨"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = RiskReportRepository(this)
        reportList = findViewById(R.id.report_list)
        feedStatus = findViewById(R.id.report_feed_status)
        loadMore = findViewById(R.id.report_load_more)
        feedSection = findViewById(R.id.report_feed_section)
        formSection = findViewById(R.id.report_form_section)

        findViewById<Button>(R.id.report_show_feed).setOnClickListener { showFeed() }
        findViewById<Button>(R.id.report_show_form).setOnClickListener { showForm() }
        findViewById<Button>(R.id.submit_report_button).setOnClickListener { submitReport() }
        findViewById<Button>(R.id.report_select_images).setOnClickListener { imagePicker.launch("image/*") }
        loadMore.setOnClickListener { loadPage(reset = false) }

        showFeed()
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized && showingFeed) loadPage(reset = true)
    }

    private fun showFeed() {
        showingFeed = true
        feedSection.visibility = View.VISIBLE
        formSection.visibility = View.GONE
        styleTab(R.id.report_show_feed, selected = true)
        styleTab(R.id.report_show_form, selected = false)
        loadPage(reset = true)
    }

    private fun showForm() {
        showingFeed = false
        feedSection.visibility = View.GONE
        formSection.visibility = View.VISIBLE
        styleTab(R.id.report_show_feed, selected = false)
        styleTab(R.id.report_show_form, selected = true)
    }

    private fun styleTab(id: Int, selected: Boolean) {
        findViewById<Button>(id).apply {
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (selected) "#252A28" else "#E9EEEB"))
            setTextColor(Color.parseColor(if (selected) "#FFFFFF" else "#343A37"))
        }
    }

    private fun loadPage(reset: Boolean) {
        if (loading) return
        if (!TokenStore(this).isLoggedIn()) {
            reportList.removeAllViews()
            feedStatus.text = "로그인하면 이웃의 안전 제보를 확인할 수 있어요."
            loadMore.visibility = View.GONE
            return
        }
        if (reset) {
            nextPage = 0
            reportList.removeAllViews()
        }
        loading = true
        feedStatus.text = "제보를 불러오는 중…"
        lifecycleScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { repository.page(nextPage) }
                page.items.forEach { reportList.addView(reportCard(it)) }
                nextPage = page.page + 1
                loadMore.visibility = if (page.hasNext) View.VISIBLE else View.GONE
                feedStatus.text = if (reportList.childCount == 0) {
                    "아직 등록된 제보가 없습니다. 첫 제보를 남겨주세요."
                } else {
                    "최신 제보 ${reportList.childCount}건"
                }
            } catch (error: Exception) {
                handleFeedError(error)
            } finally {
                loading = false
            }
        }
    }

    private fun reportCard(item: RiskReportItem): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        setBackgroundResource(R.drawable.map_panel_background)
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }

        addView(textView("${item.riskTypeLabel} · ${statusLabel(item.status)}", 16f, "#171A19", true))
        val author = if (item.mine) "내 제보" else item.authorName
        addView(textView("$author  ·  ${formatTime(item.createdAt)}", 12f, "#737A77").withTopMargin(5))
        if (item.description.isNotBlank()) {
            addView(textView(item.description, 14f, "#343A37").withTopMargin(12))
        }
        if (item.address.isNotBlank()) {
            addView(textView("위치  ${item.address}", 12f, "#737A77").withTopMargin(10))
        }
        if (item.images.isNotEmpty()) {
            addView(imageStrip(item).withTopMargin(12))
        }
        addView(textView("눌러서 자세히 보기", 12f, "#4E7B64", true).withTopMargin(12))
        setOnClickListener {
            startActivity(ReportDetailActivity.intent(this@ReportActivity, item.reportId))
        }
    }

    private fun imageStrip(item: RiskReportItem): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@ReportActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            item.images.forEachIndexed { index, image ->
                val imageView = ImageView(this@ReportActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.parseColor("#E9EEEB"))
                    contentDescription = "${item.riskTypeLabel} 제보 사진 ${index + 1}"
                    layoutParams = LinearLayout.LayoutParams(dp(230), dp(150)).apply {
                        if (index > 0) marginStart = dp(8)
                    }
                }
                addView(imageView)
                loadRemoteImage(imageView, image.url)
            }
        })
    }

    private fun loadRemoteImage(view: ImageView, url: String) {
        lifecycleScope.launch {
            val bitmap = ReportImageLoader.load(url)
            if (bitmap != null && view.isAttachedToWindow) view.setImageBitmap(bitmap)
        }
    }

    private fun submitReport() {
        if (!TokenStore(this).isLoggedIn()) {
            Toast.makeText(this, "제보를 등록하려면 로그인해 주세요.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        val riskType = when (findViewById<RadioGroup>(R.id.report_category).checkedRadioButtonId) {
            R.id.report_dark_road -> "DARK_ROAD"
            R.id.report_damaged_sidewalk -> "DAMAGED_SIDEWALK"
            R.id.report_construction -> "CONSTRUCTION"
            R.id.report_illegal_parking -> "ILLEGAL_PARKING"
            R.id.report_other -> "OTHER"
            else -> null
        }
        val detail = findViewById<EditText>(R.id.report_detail).text.toString().trim()
        if (riskType == null || detail.isEmpty()) {
            Toast.makeText(this, "제보 유형과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        PhoneLocation.refresh(this)
        val latitude = WatchState.lastLat
        val longitude = WatchState.lastLon
        if (latitude == null || longitude == null) {
            findViewById<TextView>(R.id.report_status).text =
                "현재 위치를 확인할 수 없습니다. 위치 권한과 GPS를 확인해 주세요."
            return
        }
        val submit = findViewById<Button>(R.id.submit_report_button)
        val status = findViewById<TextView>(R.id.report_status)
        submit.isEnabled = false
        status.text = "제보를 등록하는 중…"
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.create(
                        riskType,
                        latitude,
                        longitude,
                        detail,
                        findViewById<CheckBox>(R.id.report_anonymous).isChecked,
                        selectedImages.map(::readImagePart),
                    )
                }
                Toast.makeText(
                    this@ReportActivity,
                    "${result.riskTypeLabel} 제보가 등록되었습니다.",
                    Toast.LENGTH_SHORT,
                ).show()
                clearForm()
                showFeed()
            } catch (error: Exception) {
                status.text = error.message ?: "제보 등록에 실패했습니다."
            } finally {
                submit.isEnabled = true
            }
        }
    }

    private fun clearForm() {
        findViewById<EditText>(R.id.report_detail).text.clear()
        findViewById<RadioGroup>(R.id.report_category).clearCheck()
        findViewById<CheckBox>(R.id.report_anonymous).isChecked = false
        findViewById<TextView>(R.id.report_status).text = "현재 위치를 기준으로 제보합니다."
        selectedImages = emptyList()
        findViewById<TextView>(R.id.report_images_status).text = "선택된 사진 없음"
    }

    private fun handleFeedError(error: Exception) {
        if (error is ApiClient.ApiException && error.statusCode == 401) {
            feedStatus.text = "로그인이 만료되었습니다. 다시 로그인해 주세요."
            return
        }
        feedStatus.text = error.message ?: "제보 목록을 불러오지 못했습니다."
    }

    private fun readImagePart(uri: Uri): ApiClient.MultipartPart {
        val contentType = contentResolver.getType(uri).orEmpty()
        require(contentType in ALLOWED_IMAGE_TYPES) { "JPEG, PNG, WebP 사진만 첨부할 수 있습니다." }
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("선택한 사진을 읽을 수 없습니다.")
        require(bytes.size <= MAX_IMAGE_BYTES) { "사진은 한 장당 10MB 이하여야 합니다." }
        return ApiClient.MultipartPart(
            fieldName = "images",
            fileName = displayName(uri),
            contentType = contentType,
            bytes = bytes,
        )
    }

    private fun displayName(uri: Uri): String {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return name?.takeIf { it.isNotBlank() } ?: "report-image.jpg"
    }

    private fun textView(text: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun <T : View> T.withTopMargin(value: Int): T = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }
    }

    private fun statusLabel(status: String): String = when (status) {
        "APPROVED" -> "지도 반영"
        "REJECTED" -> "반려"
        else -> "확인 중"
    }

    private fun formatTime(raw: String): String = raw
        .replace('T', ' ')
        .take(16)
        .ifBlank { "시간 정보 없음" }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MAX_IMAGES = 3
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
