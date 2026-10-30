package com.safewalk.report

import android.content.Context
import com.safewalk.common.network.AuthenticatedApiClient
import com.safewalk.common.network.ApiClient

data class RiskReportResult(
    val reportId: Long,
    val riskTypeLabel: String,
    val status: String,
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
}
