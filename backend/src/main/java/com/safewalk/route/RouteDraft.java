package com.safewalk.route;

import java.util.ArrayList;
import java.util.List;

/**
 * POST /api/routes 요청을 검증하고, DB에 넣을 값으로 정리한 결과. DB 없이 만들 수 있어 단위 테스트한다.
 *
 * 규칙
 *  - 지점 2~{@value #MAX_POINTS}개, 구간 = 지점 − 1개 (구간 i는 지점 i → i+1)
 *  - 지점 종류는 순서로 정한다: 첫 지점 START, 마지막 END, 나머지 WAYPOINT. name·address는 START/END만 저장
 *  - 구간 거리·총거리는 좌표로 서버가 계산한다 (앱 값과 어긋날 일이 없게)
 *  - 구간당 시설 최대 {@value #MAX_FACILITIES_PER_SEGMENT}개
 *  - 점수: safetyScore 0~1, riskScore·appliedScore 0 이상, 시설 distance 0 이상
 */
public record RouteDraft(
        int estimatedTime,
        double safetyScore,
        double totalDistance,
        List<PointRow> points,
        List<SegmentRow> segments,
        int facilityCount
) {

    public static final int MAX_POINTS = 500;
    public static final int MAX_FACILITIES_PER_SEGMENT = 10;
    /** 예상 소요 시간 상한: 1일 (그 이상은 입력 오류로 본다) */
    public static final int MAX_ESTIMATED_SECONDS = 24 * 60 * 60;
    private static final int NAME_MAX = 100;
    private static final int ADDRESS_MAX = 255;

    public record PointRow(int seq, RoutePointType type, String name, String address, double lat, double lng) {
    }

    public record SegmentRow(int seq, double distance, double safetyScore, double riskScore,
                             List<FacilityRow> facilities) {
    }

    public record FacilityRow(SegmentFacilityType type, double lat, double lng, int quantity, double distance,
                              double appliedScore) {
    }

    public static RouteDraft from(RouteRequests.Start request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 없습니다.");
        }
        if (request.estimatedTime() == null || request.estimatedTime() < 0
                || request.estimatedTime() > MAX_ESTIMATED_SECONDS) {
            throw new IllegalArgumentException("estimatedTime(초)은 0~" + MAX_ESTIMATED_SECONDS + " 사이여야 합니다.");
        }
        double routeSafety = requireScore01(request.safetyScore(), "safetyScore");

        List<RouteRequests.Point> rawPoints = request.points();
        if (rawPoints == null || rawPoints.size() < 2) {
            throw new IllegalArgumentException("points는 출발·도착을 포함해 2개 이상이어야 합니다.");
        }
        if (rawPoints.size() > MAX_POINTS) {
            throw new IllegalArgumentException("points는 최대 " + MAX_POINTS + "개까지 보낼 수 있습니다.");
        }
        List<RouteRequests.Segment> rawSegments = request.segments();
        if (rawSegments == null || rawSegments.size() != rawPoints.size() - 1) {
            throw new IllegalArgumentException("segments 개수는 points 개수 − 1이어야 합니다. (points "
                    + rawPoints.size() + "개 → segments " + (rawPoints.size() - 1) + "개)");
        }

        int last = rawPoints.size() - 1;
        List<PointRow> points = new ArrayList<>(rawPoints.size());
        for (int i = 0; i <= last; i++) {
            RouteRequests.Point p = rawPoints.get(i);
            String where = "points[" + i + "]";
            if (p == null) {
                throw new IllegalArgumentException(where + "가 비어 있습니다.");
            }
            requireCoordinate(p.lat(), p.lng(), where);
            RoutePointType type = i == 0 ? RoutePointType.START : i == last ? RoutePointType.END : RoutePointType.WAYPOINT;
            boolean endpoint = type != RoutePointType.WAYPOINT;
            points.add(new PointRow(i, type,
                    endpoint ? optionalText(p.name(), NAME_MAX, where + ".name") : null,
                    endpoint ? optionalText(p.address(), ADDRESS_MAX, where + ".address") : null,
                    p.lat(), p.lng()));
        }

        List<SegmentRow> segments = new ArrayList<>(rawSegments.size());
        double total = 0;
        int facilityCount = 0;
        for (int i = 0; i < rawSegments.size(); i++) {
            RouteRequests.Segment s = rawSegments.get(i);
            String where = "segments[" + i + "]";
            if (s == null) {
                throw new IllegalArgumentException(where + "가 비어 있습니다.");
            }
            double safety = requireScore01(s.safetyScore(), where + ".safetyScore");
            double risk = s.riskScore() == null ? 0.0 : requireNonNegative(s.riskScore(), where + ".riskScore");
            List<FacilityRow> facilities = facilities(s.facilities(), where);
            facilityCount += facilities.size();

            PointRow from = points.get(i);
            PointRow to = points.get(i + 1);
            double distance = GeoDistance.meters(from.lat(), from.lng(), to.lat(), to.lng());
            total += distance;
            segments.add(new SegmentRow(i, distance, safety, risk, facilities));
        }

        return new RouteDraft(request.estimatedTime(), routeSafety, total, points, segments, facilityCount);
    }

    private static List<FacilityRow> facilities(List<RouteRequests.Facility> raw, String segmentWhere) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_FACILITIES_PER_SEGMENT) {
            throw new IllegalArgumentException(segmentWhere + ".facilities는 최대 "
                    + MAX_FACILITIES_PER_SEGMENT + "개까지 보낼 수 있습니다.");
        }
        List<FacilityRow> rows = new ArrayList<>(raw.size());
        for (int j = 0; j < raw.size(); j++) {
            RouteRequests.Facility f = raw.get(j);
            String where = segmentWhere + ".facilities[" + j + "]";
            if (f == null) {
                throw new IllegalArgumentException(where + "가 비어 있습니다.");
            }
            requireCoordinate(f.lat(), f.lng(), where);
            int quantity = f.quantity() == null ? 1 : f.quantity();
            if (quantity < 1) {
                throw new IllegalArgumentException(where + ".quantity는 1 이상이어야 합니다.");
            }
            rows.add(new FacilityRow(
                    SegmentFacilityType.from(f.type()),
                    f.lat(), f.lng(), quantity,
                    requireNonNegative(f.distance(), where + ".distance"),
                    requireNonNegative(f.appliedScore(), where + ".appliedScore")));
        }
        return rows;
    }

    private static void requireCoordinate(Double lat, Double lng, String where) {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException(where + "에 lat·lng가 모두 있어야 합니다.");
        }
        if (!Double.isFinite(lat) || lat < -90 || lat > 90 || !Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException(where + "의 좌표 범위가 올바르지 않습니다.");
        }
    }

    private static double requireScore01(Double value, String name) {
        if (value == null || !Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + "는 0~1 사이 값이어야 합니다.");
        }
        return value;
    }

    private static double requireNonNegative(Double value, String name) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + "는 0 이상의 값이어야 합니다.");
        }
        return value;
    }

    private static String optionalText(String raw, int max, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(name + "는 " + max + "자 이하여야 합니다.");
        }
        return trimmed;
    }
}
