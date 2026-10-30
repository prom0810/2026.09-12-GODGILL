package com.safewalk.emergency;

/**
 * emergency_event.type (DB ck_emergency_type).
 *
 * 지금은 SOS(사용자가 직접 누름)만 만들 수 있다. FALL·ABNORMAL_HEART_RATE(자동 감지)는
 * DB 제약(ck_emergency_auto_detect_context)상 wearable_id와 segment_id가 필수라서
 * 웨어러블·경로 기능이 구현된 뒤에 연다.
 */
public enum EmergencyType {
    SOS,
    FALL,
    ABNORMAL_HEART_RATE
}
