package com.safewalk.report;

/** 위험구역 출처. risk_area.source CHECK 제약과 같은 값이다. */
public enum RiskAreaSource {
    /** 사용자 위험 제보를 승인해 만든 구역 (승인 기능은 아직 없음) */
    USER_REPORT,
    /** 공공데이터·운영자가 넣은 구역 (현재 목업 시드 포함) */
    PUBLIC_DATA
}
