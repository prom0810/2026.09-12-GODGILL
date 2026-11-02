package com.safewalk.emergency;

/**
 * emergency_event_log.event_type (DB ck_emergency_log_event_type). 사건 처리 이력.
 *
 * - CREATED: SOS 생성
 * - GUARDIAN_PUSHED: 앱 연결 보호자에게 알림 발송 (message에 대상 수)
 * - GUARDIAN_SMS_SENT: 대표 보호자에게 문자 발송
 * - LOCATION_LOST: 위치 수신이 3분 끊겨 보호자에게 알림
 * - RESOLVED / FALSE_ALARM / CANCELLED: 사용자·보호자 종료 (누가 했는지는 emergency_event.resolved_by_user_id)
 * - AUTO_CLOSED: 2시간 경과 자동 종료
 */
public enum EmergencyLogType {
    CREATED,
    GUARDIAN_PUSHED,
    GUARDIAN_SMS_SENT,
    LOCATION_LOST,
    RESOLVED,
    FALSE_ALARM,
    CANCELLED,
    AUTO_CLOSED
}
