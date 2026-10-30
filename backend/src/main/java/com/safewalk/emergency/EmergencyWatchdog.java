package com.safewalk.emergency;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 진행 중인 SOS를 주기적으로(기본 5초) 살핀다.
 *
 * | 조건                                  | 동작                                           |
 * |---------------------------------------|------------------------------------------------|
 * | 마지막 위치 수신 후 staleSeconds(30)  | SSE location_stale → 보호자 화면 "마지막 위치" |
 * | 마지막 위치 수신 후 lostSeconds(180)  | 위치 열람 보호자에게 "위치 수신 끊김" 푸시 1회 |
 * | 발생 후 autoCloseSeconds(7200)        | 자동 종료(CANCELLED, 종료자 없음)              |
 *
 * - 위치가 다시 들어오면 두 알림 모두 다시 보낼 수 있게 된다 (마지막 수신 시각이 바뀌므로).
 * - 위치가 한 번도 안 왔으면 발생 시각, 서버가 재시작됐으면 재시작 시각부터 센다 (메모리 위치가 비므로).
 */
@Component
public class EmergencyWatchdog {

    private static final Logger log = LoggerFactory.getLogger(EmergencyWatchdog.class);

    private final EmergencyEventRepository eventRepository;
    private final EmergencyService emergencyService;
    private final LocationStore locationStore;
    private final EmergencySseHub sseHub;
    private final EmergencyProperties properties;
    private final Instant startedAt = Instant.now();

    /** 사건별로 "이 수신 시각에 대해서는 이미 알렸다" 표시 */
    private final Map<Long, Instant> staleSentFor = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lostSentFor = new ConcurrentHashMap<>();

    public EmergencyWatchdog(EmergencyEventRepository eventRepository, EmergencyService emergencyService,
                             LocationStore locationStore, EmergencySseHub sseHub, EmergencyProperties properties) {
        this.eventRepository = eventRepository;
        this.emergencyService = emergencyService;
        this.locationStore = locationStore;
        this.sseHub = sseHub;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${emergency.watchdog-interval-ms:5000}",
            initialDelayString = "${emergency.watchdog-interval-ms:5000}")
    public void check() {
        Instant now = Instant.now();
        List<EmergencyEvent> open;
        try {
            open = eventRepository.findByStatusIn(EmergencyEventRepository.OPEN_STATUSES);
        } catch (RuntimeException e) {
            log.warn("진행 중 SOS 조회 실패: {}", e.getMessage());
            return;
        }

        Set<Long> openIds = new HashSet<>();
        for (EmergencyEvent event : open) {
            Long id = event.getEmergencyId();
            Instant occurred = event.getOccurredAt().toInstant();
            try {
                if (Duration.between(occurred, now).getSeconds() >= properties.autoCloseSeconds()) {
                    emergencyService.autoClose(id);
                    log.info("SOS 자동 종료 (emergencyId={})", id);
                    continue;
                }
                openIds.add(id);

                Instant lastReceived = locationStore.lastReceivedAt(id)
                        .orElse(occurred.isAfter(startedAt) ? occurred : startedAt);
                long silent = Duration.between(lastReceived, now).getSeconds();
                OffsetDateTime lastRecorded = locationStore.latest(id)
                        .map(LocationPoint::recordedAt)
                        .orElse(event.getOccurredAt());

                if (silent >= properties.staleSeconds() && !lastReceived.equals(staleSentFor.get(id))) {
                    sseHub.publishStale(id, lastRecorded);
                    staleSentFor.put(id, lastReceived);
                }
                if (silent >= properties.lostSeconds() && !lastReceived.equals(lostSentFor.get(id))) {
                    emergencyService.alertLocationLost(id, lastRecorded);
                    lostSentFor.put(id, lastReceived);
                }
            } catch (RuntimeException e) {
                log.warn("SOS 자동 처리 실패 (emergencyId={}): {}", id, e.getMessage(), e);
            }
        }
        staleSentFor.keySet().retainAll(openIds);
        lostSentFor.keySet().retainAll(openIds);
    }
}
