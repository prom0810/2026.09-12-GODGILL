package com.safewalk.route;

import java.time.OffsetDateTime;
import java.util.List;

/** 경로 API 응답 본문 */
public final class RouteResponses {

    private RouteResponses() {
    }

    /**
     * POST /api/routes 응답.
     * cancelledRouteId: 진행 중이던 경로가 있어 자동으로 취소했다면 그 ID, 없으면 null
     */
    public record Started(
            Long routeId,
            String status,
            double totalDistance,
            int estimatedTime,
            double safetyScore,
            int pointCount,
            int segmentCount,
            int facilityCount,
            OffsetDateTime createdAt,
            Long cancelledRouteId
    ) {
    }

    /** 출발지·도착지 */
    public record Place(String name, String address, double lat, double lng) {

        static Place from(RoutePoint point) {
            return point == null ? null
                    : new Place(point.getName(), point.getAddress(), point.getLat(), point.getLng());
        }
    }

    /**
     * 경로 요약 (목록, 진행 중 경로, 상태 변경 응답).
     * start/end는 65일이 지나 상세가 정리된 뒤에도 남는다.
     */
    public record Summary(
            Long routeId,
            String status,
            String userType,
            double totalDistance,
            int estimatedTime,
            double safetyScore,
            Place start,
            Place end,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt
    ) {

        static Summary of(Route route, RoutePoint start, RoutePoint end) {
            return new Summary(
                    route.getRouteId(),
                    route.getStatus().name(),
                    route.getUserType().getTypeName(),
                    route.getTotalDistance(),
                    route.getEstimatedTime(),
                    route.getSafetyScore(),
                    Place.from(start),
                    Place.from(end),
                    route.getCreatedAt(),
                    route.getCompletedAt()
            );
        }
    }

    /** GET /api/routes */
    public record Page(List<Summary> items, int page, int size, boolean hasNext) {
    }

    public record Point(int seq, String type, String name, String address, double lat, double lng) {

        static Point from(RoutePoint p) {
            return new Point(p.getSeq(), p.getPointType().name(), p.getName(), p.getAddress(), p.getLat(), p.getLng());
        }
    }

    public record Facility(String type, double lat, double lng, int quantity, double distance, double appliedScore) {

        static Facility from(SegmentFacility f) {
            return new Facility(f.getFacilityType(), f.getLat(), f.getLng(), f.getQuantity(), f.getDistance(),
                    f.getAppliedScore());
        }
    }

    /** seq = 시작 지점의 seq (구간 i는 지점 i → i+1) */
    public record Segment(Long segmentId, int seq, double distance, double safetyScore, double riskScore,
                          List<Facility> facilities) {
    }

    /**
     * GET /api/routes/{routeId}.
     * detailAvailable=false면 보관 기간(기본 65일)이 지나 경유 지점·구간이 정리된 경로다.
     * 이때 points에는 출발·도착 2개만 있고 segments는 비어 있다.
     */
    public record Detail(
            Summary summary,
            boolean detailAvailable,
            List<Point> points,
            List<Segment> segments
    ) {
    }
}
