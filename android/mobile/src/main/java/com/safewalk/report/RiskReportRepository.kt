package com.safewalk.report

import android.content.Context
import com.safewalk.common.network.AuthenticatedApiClient
import com.safewalk.common.network.ApiClient
import org.json.JSONObject

data class RiskReportResult(
    val reportId: Long,
    val riskTypeLabel: String,
    val status: String,
)

data class RiskReportImage(
    val imageId: Long,
    val url: String,
)

data class RiskReportItem(
    val reportId: Long,
    val riskTypeLabel: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val description: String,
    val authorName: String,
    val mine: Boolean,
    val status: String,
    val createdAt: String,
    val images: List<RiskReportImage>,
)

data class RiskReportPage(
    val items: List<RiskReportItem>,
    val page: Int,
    val hasNext: Boolean,
)

class RiskReportRepository(context: Context) {
    private val api = AuthenticatedApiClient(context)

    fun create(
        riskType: String,
        latitude: Double,
        longitude: Double,
        description: String,
        anonymous: Boolean,
        images: List<ApiClient.MultipartPart> = emptyList(),
    ): RiskReportResult {
        val data = api.postMultipartObject(
            "/api/risk-reports",
            mapOf(
                "riskType" to riskType,
                "lat" to latitude.toString(),
                "lng" to longitude.toString(),
                "description" to description,
                "anonymous" to anonymous.toString(),
            ),
            images,
        )
        return RiskReportResult(
            reportId = data.getLong("reportId"),
            riskTypeLabel = data.getString("riskTypeLabel"),
            status = data.getString("status"),
        )
    }

    fun page(page: Int, size: Int = 20): RiskReportPage {
        val data = api.getObject("/api/risk-reports?page=$page&size=$size")
        val items = data.optJSONArray("items")
        return RiskReportPage(
            items = buildList {
                if (items != null) {
                    for (index in 0 until items.length()) {
                        add(parseItem(items.getJSONObject(index)))
                    }
                }
            },
            page = data.optInt("page", page),
            hasNext = data.optBoolean("hasNext"),
        )
    }

    fun detail(reportId: Long): RiskReportItem =
        parseItem(api.getObject("/api/risk-reports/$reportId"))

    private fun parseItem(data: JSONObject): RiskReportItem {
        val images = data.optJSONArray("images")
        return RiskReportItem(
            reportId = data.getLong("reportId"),
            riskTypeLabel = data.optString("riskTypeLabel", "안전 제보"),
            latitude = data.optDouble("lat"),
            longitude = data.optDouble("lng"),
            address = data.stringOrEmpty("address"),
            description = data.stringOrEmpty("description"),
            authorName = data.optString("authorName", "익명"),
            mine = data.optBoolean("mine"),
            status = data.optString("status", "PENDING"),
            createdAt = data.optString("createdAt"),
            images = buildList {
                if (images != null) {
                    for (index in 0 until images.length()) {
                        val image = images.getJSONObject(index)
                        val url = image.optString("url")
                        if (url.isNotBlank()) {
                            add(RiskReportImage(image.getLong("imageId"), url))
                        }
                    }
                }
            },
        )
    }

    private fun JSONObject.stringOrEmpty(name: String): String =
        if (isNull(name)) "" else optString(name)
}
