package com.safewalk.route;

import java.util.List;

/** 경로 API 요청 본문 */
public final class RouteRequests {

    private RouteRequests() {
    }

    /**
     * POST /api/routes — 안내 시작. 앱이 계산한 경로를 그대로 보낸다.
     * <pre>
     * {
     *   "estimatedTime": 1140,          // 예상 소요 시간(초)
     *   "safetyScore": 0.74,            // 화면에 보여 준 경로 평균 안전점수 (0~1)
     *   "points": [                     // A* 경로의 지점, 출발 → 도착 순서 (2~500개)
     *     { "lat": 36.8100, "lng": 127.1467, "name": "천안역", "address": "충남 천안시 ..." },
     *     { "lat": 36.8104, "lng": 127.1471 },
     *     { "lat": 36.8120, "lng": 127.1490, "name": "천안시청" }
     *   ],
     *   "segments": [                   // points[i] → points[i+1] 구간, 개수 = points − 1
     *     { "safetyScore": 0.82, "riskScore": 0.0,
     *       "facilities": [ { "type": "CCTV", "lat": 36.8102, "lng": 127.1468,
     *                         "quantity": 2, "distance": 35.2, "appliedScore": 0.115 } ] },
     *     { "safetyScore": 0.40, "riskScore": 0.42, "facilities": [] }
     *   ]
     * }
     * </pre>
     * 거리(구간·총거리)는 서버가 좌표로 계산하므로 보내지 않는다. name·address는 첫/마지막 지점에만 저장한다.
     */
    public record Start(Integer estimatedTime, Double safetyScore, List<Point> points, List<Segment> segments) {
    }

    public record Point(Double lat, Double lng, String name, String address) {
    }

    /** riskScore 생략 시 0, facilities 생략 시 빈 목록 (앱이 점수 내역을 못 보내는 경우 대비) */
    public record Segment(Double safetyScore, Double riskScore, List<Facility> facilities) {
    }

    /** quantity 생략 시 1 */
    public record Facility(String type, Double lat, Double lng, Integer quantity, Double distance,
                           Double appliedScore) {
    }

    /**
     * PATCH /api/routes/{routeId}
     * { "status": "COMPLETED" }  도착
     * { "status": "CANCELLED" }  안내 취소
     */
    public record UpdateStatus(RouteStatus status) {
    }
}
