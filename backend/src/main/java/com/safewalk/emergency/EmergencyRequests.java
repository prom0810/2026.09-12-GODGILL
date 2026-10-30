package com.safewalk.emergency;

import java.time.OffsetDateTime;

/** SOS API 요청 본문 */
public final class EmergencyRequests {

    private EmergencyRequests() {
    }

    /**
     * POST /api/emergencies
     * { "type": "SOS", "source": "MOBILE", "lat": 36.81, "lng": 127.14, "accuracy": 12.5 }
     * type 생략 시 SOS. lat/lng는 GPS를 못 잡았으면 둘 다 생략.
     * wearableId·routeId·segmentId는 웨어러블·경로 기능 구현 후 받는다 (지금 보내면 400).
     */
    public record Start(EmergencyType type, EmergencySource source, Double lat, Double lng, Double accuracy,
                        Long wearableId, Long routeId, Long segmentId) {
    }

    /**
     * POST /api/emergencies/{id}/locations
     * { "lat": 36.81, "lng": 127.14, "accuracy": 8.0, "recordedAt": "2026-10-30T21:03:15+09:00" }
     * recordedAt은 폰에서 측정한 시각. 생략하면 서버가 받은 시각.
     */
    public record Location(Double lat, Double lng, Double accuracy, OffsetDateTime recordedAt) {
    }

    /**
     * PATCH /api/emergencies/{id}
     * { "status": "RESOLVED" }   사용자: RESOLVED / FALSE_ALARM / CANCELLED, 보호자: RESOLVED만
     */
    public record Resolve(EmergencyStatus status) {
    }
}
