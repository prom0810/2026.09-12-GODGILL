package com.safewalk.report;

/**
 * GET /api/risk-areas 항목.
 * { "riskAreaId": 1, "riskType": "OTHER", "riskTypeLabel": "기타", "source": "PUBLIC_DATA",
 *   "lat": 36.8046, "lng": 127.1501, "radius": 250.0, "riskScore": 0.7, "description": "..." }
 */
public record RiskAreaResponse(
        Long riskAreaId,
        String riskType,
        String riskTypeLabel,
        String source,
        double lat,
        double lng,
        double radius,
        double riskScore,
        String description
) {
    public static RiskAreaResponse from(RiskArea area) {
        return new RiskAreaResponse(
                area.getRiskAreaId(),
                area.getRiskType().name(),
                area.getRiskType().getLabel(),
                area.getSource().name(),
                area.getLat(),
                area.getLng(),
                area.getRadius(),
                area.getRiskScore(),
                area.getDescription()
        );
    }
}
