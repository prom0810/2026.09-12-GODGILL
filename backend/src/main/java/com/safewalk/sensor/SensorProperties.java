package com.safewalk.sensor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 워치 센서 수집 설정 (application.yml의 sensor.*).
 *
 *  - maxBatchBytes : gzip을 푼 배치 최대 크기 (기본 1MB)
 *  - bpmTtlSeconds : Redis 심박 값 보관 한도. SOS 자동 종료(기본 2시간)보다 15분 길게 (종료 시 bpm_log 저장 후 삭제)
 */
@Component
public class SensorProperties {

    private final int maxBatchBytes;
    private final long bpmTtlSeconds;

    public SensorProperties(@Value("${sensor.max-batch-bytes:1048576}") int maxBatchBytes,
                            @Value("${emergency.auto-close-seconds:7200}") long emergencyAutoCloseSeconds) {
        if (maxBatchBytes < 1024) {
            throw new IllegalStateException("sensor.max-batch-bytes는 1024 이상이어야 합니다.");
        }
        this.maxBatchBytes = maxBatchBytes;
        this.bpmTtlSeconds = emergencyAutoCloseSeconds + 900;
    }

    public int maxBatchBytes() { return maxBatchBytes; }
    public long bpmTtlSeconds() { return bpmTtlSeconds; }
}
