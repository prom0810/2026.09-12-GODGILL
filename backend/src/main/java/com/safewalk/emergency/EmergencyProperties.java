package com.safewalk.emergency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SOS 시간 설정 (application.yml의 emergency.*, .env로 바꿀 수 있음).
 * 테스트할 때는 .env에서 짧게 줄여 자동 처리를 바로 확인할 수 있다.
 *
 * - staleSeconds (30): 위치가 이 시간 동안 안 오면 보호자 화면에 "마지막 위치" 표시 (SSE location_stale)
 * - lostSeconds (180): 이 시간 동안 안 오면 보호자에게 "위치 수신 끊김" 푸시 1회
 * - autoCloseSeconds (7200): 발생 후 이 시간이 지나면 자동 종료(CANCELLED)
 * - sseTimeoutMinutes (30): 보호자 SSE 연결 최대 유지 시간. 끊기면 앱이 다시 연결
 */
@Component
public class EmergencyProperties {

    private final long staleSeconds;
    private final long lostSeconds;
    private final long autoCloseSeconds;
    private final long sseTimeoutMinutes;

    public EmergencyProperties(
            @Value("${emergency.stale-seconds:30}") long staleSeconds,
            @Value("${emergency.lost-seconds:180}") long lostSeconds,
            @Value("${emergency.auto-close-seconds:7200}") long autoCloseSeconds,
            @Value("${emergency.sse-timeout-minutes:30}") long sseTimeoutMinutes) {
        this.staleSeconds = staleSeconds;
        this.lostSeconds = lostSeconds;
        this.autoCloseSeconds = autoCloseSeconds;
        this.sseTimeoutMinutes = sseTimeoutMinutes;
    }

    public long staleSeconds() {
        return staleSeconds;
    }

    public long lostSeconds() {
        return lostSeconds;
    }

    public long autoCloseSeconds() {
        return autoCloseSeconds;
    }

    public long sseTimeoutMinutes() {
        return sseTimeoutMinutes;
    }
}
