package com.safewalk.notification;

/**
 * notification.reference_type 값 (DB ck_notification_reference_type).
 * 알림을 눌렀을 때 앱이 어느 화면으로 갈지 정하는 데 쓴다. reference_id는 FK가 아니라 값만 저장한다.
 *
 * - GUARDIAN: guardian.guardian_id (보호자 연결 알림)
 * - EMERGENCY_EVENT: emergency_event.emergency_id (SOS)
 * - RISK_REPORT, ROUTE: 각 테이블의 PK
 */
public enum ReferenceType {
    RISK_REPORT,
    ROUTE,
    GUARDIAN,
    EMERGENCY_EVENT
}
