package com.safewalk.report;

/**
 * 제보 또는 제보 사진을 찾을 수 없는 경우 (존재하지 않음, 다른 사용자의 제보).
 * 다른 사용자의 제보인지 여부를 드러내지 않기 위해 두 경우 모두 같은 404로 응답한다.
 */
public class RiskReportNotFoundException extends RuntimeException {

    public RiskReportNotFoundException(String message) {
        super(message);
    }
}
