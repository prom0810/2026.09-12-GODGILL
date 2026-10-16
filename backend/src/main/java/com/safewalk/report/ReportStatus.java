package com.safewalk.report;

/**
 * 제보 처리 상태. risk_report.status CHECK 제약과 같은 값이다.
 * APPROVED가 되면 risk_area_id도 함께 채워야 한다(DB 제약 ck_risk_report_area_when_approved).
 */
public enum ReportStatus {
    PENDING,
    APPROVED,
    REJECTED
}
