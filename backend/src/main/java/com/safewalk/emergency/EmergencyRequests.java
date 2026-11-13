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
     * wearableId·routeId·segmentId는 서버가 정한다 (보내면 400).
     * 워치 감지: { "type": "FALL" | "ABNORMAL_HEART_RATE", "source": "WEARABLE", lat, lng, "bpm": 132 }
     *   bpm = 워치가 감지할 때 잰 심박(20~250, 선택). 심박 이상 SOS면 bpm_log.bpm_start로 저장된다
     */
    public record Start(EmergencyType type, EmergencySource source, Double lat, Double lng, Double accuracy,
                        Long wearableId, Long routeId, Long segmentId, Integer bpm) {
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
