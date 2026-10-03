package com.safewalk.guardian;

import java.util.Locale;

/**
 * 보호자와 사용자의 관계 (고정 선택지).
 * DB(guardian.relationship)와 API에는 코드(PARENT/FAMILY/ACQUAINTANCE)로 주고받고,
 * 화면에 표시할 한글은 label을 사용한다.
 */
public enum Relationship {
    PARENT("부모"),
    FAMILY("가족"),
    ACQUAINTANCE("지인");

    private final String label;

    Relationship(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 요청 값을 Relationship으로 변환한다. 코드("PARENT", 대소문자 무관)와 한글("부모") 모두 허용한다.
     *
     * @throws IllegalArgumentException 세 가지 중 어느 것도 아닌 경우(400)
     */
    public static Relationship from(String raw) {
        String value = raw == null ? "" : raw.trim();
        for (Relationship r : values()) {
            if (r.name().equals(value.toUpperCase(Locale.ROOT)) || r.label.equals(value)) {
                return r;
            }
        }
        throw new IllegalArgumentException(
                "보호자 관계는 PARENT(부모), FAMILY(가족), ACQUAINTANCE(지인) 중 하나여야 합니다.");
    }
}
