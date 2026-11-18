package com.safewalk.report;

/** 위험구역 상태. risk_area.status CHECK 제약과 같은 값이다. 해결되면 삭제하지 않고 RESOLVED로 둔다. */
public enum RiskAreaStatus {
    ACTIVE,
    RESOLVED
}
