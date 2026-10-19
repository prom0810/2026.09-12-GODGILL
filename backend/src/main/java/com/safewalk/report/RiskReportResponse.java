package com.safewalk.report;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 제보 응답. riskType은 코드(DARK_ROAD 등), riskTypeLabel은 화면 표시용 한글.
 */
public record RiskReportResponse(
        Long reportId,
        String riskType,
        String riskTypeLabel,
        double lat,
        double lng,
        String address,
        String description,
        boolean anonymous,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<RiskReportImageResponse> images
) {

    public static RiskReportResponse of(RiskReport report, List<RiskReportImageResponse> images) {
        return new RiskReportResponse(
                report.getReportId(),
                report.getRiskType().name(),
                report.getRiskType().getLabel(),
                report.getLat(),
                report.getLng(),
                report.getAddress(),
                report.getDescription(),
                report.isAnonymous(),
                report.getStatus().name(),
                report.getCreatedAt(),
                report.getUpdatedAt(),
                images
        );
    }
}
