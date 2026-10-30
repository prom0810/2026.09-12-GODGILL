package com.safewalk.emergency;

/**
 * emergency_event.status (DB ck_emergency_status).
 *
 * 진행 중: OCCURRED(발생), IN_PROGRESS(예약 — 지금은 쓰지 않음)
 * 종료: RESOLVED(상황 해결), FALSE_ALARM(오작동), CANCELLED(취소·자동 종료)
 *
 * 사용자당 진행 중인 사건은 1건만 있을 수 있다 (DB uq_emergency_one_open).
 */
public enum EmergencyStatus {
    OCCURRED,
    IN_PROGRESS,
    RESOLVED,
    FALSE_ALARM,
    CANCELLED;

    public boolean isOpen() {
        return this == OCCURRED || this == IN_PROGRESS;
    }
}
