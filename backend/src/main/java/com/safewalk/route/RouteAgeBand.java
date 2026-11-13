package com.safewalk.route;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Period;

/** 경로 가중치에만 사용하는 연령대. 회원 유형 ADULT/MINOR와 독립적이다. */
public enum RouteAgeBand {
    UNDER_14, AGE_14_PLUS;

    public static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    public static void validateBirthDate(LocalDate birthDate, LocalDate today) {
        if (birthDate == null || birthDate.isBefore(LocalDate.of(1900, 1, 1)) || birthDate.isAfter(today)) {
            throw new IllegalArgumentException("생년월일을 1900-01-01부터 오늘 사이의 날짜로 입력해 주세요.");
        }
    }

    public static RouteAgeBand from(LocalDate birthDate, LocalDate today) {
        validateBirthDate(birthDate, today);
        return Period.between(birthDate, today).getYears() < 14 ? UNDER_14 : AGE_14_PLUS;
    }
}
