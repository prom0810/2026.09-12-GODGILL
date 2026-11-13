package com.safewalk.emergency;

/**
 * emergency_event.type (DB ck_emergency_type).
 *
 * - SOS: 사용자가 직접 누름 (폰·워치 버튼)
 * - FALL / ABNORMAL_HEART_RATE: 워치가 감지 → 폰 팝업에 응답이 없거나 사용자가 도움 요청 (source=WEARABLE).
 *   경로 안내 중에만 받는다. DB 제약(ck_emergency_auto_detect_context)상 wearable_id·segment_id가 필수라
 *   서버가 진행 중 경로·연결 워치·가장 가까운 구간으로 채운다 ({@link com.safewalk.sensor.SensorContextResolver}).
 */
public enum EmergencyType {
    SOS,
    FALL,
    ABNORMAL_HEART_RATE
}
