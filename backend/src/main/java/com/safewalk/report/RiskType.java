package com.safewalk.report;

import java.util.Arrays;
import java.util.Locale;

/** 위험 제보 유형. risk_report.risk_type CHECK 제약과 같은 값이다. */
public enum RiskType {
    DARK_ROAD("어두운 길"),
    DAMAGED_SIDEWALK("파손된 보도"),
    CONSTRUCTION("공사 구간"),
    ILLEGAL_PARKING("불법 주정차"),
    OTHER("기타");

    private final String label;

    RiskType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 코드(DARK_ROAD) 또는 한글 라벨(어두운 길) 모두 허용한다. */
    public static RiskType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("위험 유형(riskType)을 입력해 주세요.");
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(t -> t.name().equals(trimmed.toUpperCase(Locale.ROOT)) || t.label.equals(trimmed))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "위험 유형은 DARK_ROAD, DAMAGED_SIDEWALK, CONSTRUCTION, ILLEGAL_PARKING, OTHER 중 하나여야 합니다."));
    }
}
