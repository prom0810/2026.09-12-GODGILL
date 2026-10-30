package com.safewalk.global;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업(@Scheduled) 켜기.
 * 사용처: SOS 위치 끊김·자동 종료 검사(EmergencyWatchdog), SSE 연결 유지 ping(EmergencySseHub)
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
