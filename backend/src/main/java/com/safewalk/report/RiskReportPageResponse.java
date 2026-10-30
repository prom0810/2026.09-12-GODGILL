package com.safewalk.report;

import java.util.List;

/** GET /api/risk-reports — 최신 제보 목록. */
public record RiskReportPageResponse(
        List<RiskReportResponse> items,
        int page,
        int size,
        boolean hasNext
) {
}
