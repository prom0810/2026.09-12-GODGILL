package com.safewalk.report.view

import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import com.safewalk.R
import com.safewalk.auth.TokenStore
import com.safewalk.common.network.ApiClient
import com.safewalk.login.LoginActivity
import com.safewalk.main.navigation.BaseMainTabActivity
import com.safewalk.main.navigation.MainTab
import com.safewalk.report.RiskReportRepository
import com.safewalk.watch.PhoneLocation
import com.safewalk.watch.WatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportActivity : BaseMainTabActivity() {
    override val layoutResource: Int = R.layout.activity_report
    override val selectedTab: MainTab = MainTab.REPORT
    private var selectedImages: List<Uri> = emptyList()
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImages = uris.distinct().take(MAX_IMAGES)
        findViewById<TextView>(R.id.report_images_status).text =
            if (selectedImages.isEmpty()) "선택된 사진 없음" else "사진 ${selectedImages.size}장 선택됨"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<Button>(R.id.submit_report_button).setOnClickListener {
            submitReport()
        }
        findViewById<Button>(R.id.report_select_images).setOnClickListener {
            imagePicker.launch("image/*")
        }
    }

    private fun submitReport() {
        if (!TokenStore(this).isLoggedIn()) {
            Toast.makeText(this, "제보를 등록하려면 로그인해 주세요.", Toast.LENGTH_SHORT).show()
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            return
        }
        val checkedId = findViewById<RadioGroup>(R.id.report_category).checkedRadioButtonId
        val riskType = when (checkedId) {
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
            findViewById<TextView>(R.id.report_status).text = "현재 위치를 확인할 수 없습니다. 위치 권한과 GPS를 확인해 주세요."
            return
        }
        val submit = findViewById<Button>(R.id.submit_report_button)
        val status = findViewById<TextView>(R.id.report_status)
        submit.isEnabled = false
        status.text = "제보를 등록하는 중…"
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    RiskReportRepository(this@ReportActivity).create(
                        riskType,
                        latitude,
                        longitude,
                        detail,
                        findViewById<CheckBox>(R.id.report_anonymous).isChecked,
                        selectedImages.map(::readImagePart),
                    )
                }
                status.text = "${result.riskTypeLabel} 제보 #${result.reportId}가 접수되었습니다."
                findViewById<EditText>(R.id.report_detail).text.clear()
                findViewById<RadioGroup>(R.id.report_category).clearCheck()
                findViewById<CheckBox>(R.id.report_anonymous).isChecked = false
                selectedImages = emptyList()
                findViewById<TextView>(R.id.report_images_status).text = "선택된 사진 없음"
            } catch (error: Exception) {
                status.text = error.message ?: "제보 등록에 실패했습니다."
            } finally {
                submit.isEnabled = true
            }
        }
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

    private companion object {
        const val MAX_IMAGES = 3
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
