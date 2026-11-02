package com.safewalk.emergency;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 보호자 앱과 열어 둔 SSE 연결 관리. 사건마다 연결 여러 개(보호자 여러 명, 기기 여러 대)를 둔다.
 *
 * 보내는 이벤트
 *  - location        {lat, lng, accuracy, recordedAt}   새 위치가 들어올 때마다
 *  - location_stale  {lastRecordedAt}                   위치가 30초 동안 안 들어옴 (다음 location으로 해제)
 *  - status          {status, resolvedAt}               사건 종료 → 직후 연결을 닫는다
 *  - :ping (주석)                                       20초마다, 중간 장비가 연결을 끊지 않게
 *
 * 연결이 끊긴 보호자는 보내기 실패로 감지해 목록에서 지운다.
 */
@Component
public class EmergencySseHub {

    private static final Logger log = LoggerFactory.getLogger(EmergencySseHub.class);

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final EmergencyProperties properties;

    public EmergencySseHub(EmergencyProperties properties) {
        this.properties = properties;
    }

    public record StaleEvent(OffsetDateTime lastRecordedAt) {
    }

    public record StatusEvent(EmergencyStatus status, OffsetDateTime resolvedAt) {
    }

    /** 새 연결 등록. 연결 직후 보낼 위치가 있으면 바로 1건 보낸다. */
    public SseEmitter subscribe(Long emergencyId, LocationPoint initial) {
        SseEmitter emitter = new SseEmitter(properties.sseTimeoutMinutes() * 60_000L);
        Set<SseEmitter> set = emitters.computeIfAbsent(emergencyId, id -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        Runnable cleanup = () -> remove(emergencyId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 연결이 열렸음을 바로 알리기 위해 주석 한 줄을 먼저 보낸다 (프록시가 응답을 붙잡아 두지 않게)
        if (!send(emitter, SseEmitter.event().comment("connected"))) {
            return emitter;
        }
        if (initial != null) {
            send(emitter, SseEmitter.event().name("location").data(initial, MediaType.APPLICATION_JSON));
        }
        return emitter;
    }

    /** 이미 끝난 사건에 연결한 경우: 상태 1건 보내고 바로 닫는다 */
    public SseEmitter closedStream(StatusEvent status) {
        SseEmitter emitter = new SseEmitter(10_000L);
        send(emitter, SseEmitter.event().name("status").data(status, MediaType.APPLICATION_JSON));
        emitter.complete();
        return emitter;
    }

    public void publishLocation(Long emergencyId, LocationPoint point) {
        broadcast(emergencyId, () -> SseEmitter.event().name("location").data(point, MediaType.APPLICATION_JSON));
    }

    public void publishStale(Long emergencyId, OffsetDateTime lastRecordedAt) {
        StaleEvent stale = new StaleEvent(lastRecordedAt);
        broadcast(emergencyId, () -> SseEmitter.event().name("location_stale")
                .data(stale, MediaType.APPLICATION_JSON));
    }

    /** 종료 알림을 보내고 그 사건의 연결을 모두 닫는다 */
    public void publishStatusAndClose(Long emergencyId, StatusEvent status) {
        Set<SseEmitter> set = emitters.remove(emergencyId);
        if (set == null) {
            return;
        }
        for (SseEmitter emitter : set) {
            if (send(emitter, SseEmitter.event().name("status").data(status, MediaType.APPLICATION_JSON))) {
                emitter.complete();
            }
        }
    }

    public int connectionCount(Long emergencyId) {
        Set<SseEmitter> set = emitters.get(emergencyId);
        return set == null ? 0 : set.size();
    }

    @Scheduled(fixedRate = 20_000L)
    public void heartbeat() {
        emitters.forEach((id, set) -> {
            for (SseEmitter emitter : set) {
                if (!send(emitter, SseEmitter.event().comment("ping"))) {
                    remove(id, emitter);
                }
            }
        });
    }

    /** 이벤트 빌더는 한 번 보내면 재사용할 수 없어서 연결마다 새로 만든다 */
    private void broadcast(Long emergencyId, Supplier<SseEmitter.SseEventBuilder> event) {
        Set<SseEmitter> set = emitters.get(emergencyId);
        if (set == null) {
            return;
        }
        for (SseEmitter emitter : set) {
            if (!send(emitter, event.get())) {
                remove(emergencyId, emitter);
            }
        }
    }

    /** 보내기 실패(앱이 연결을 끊음 등)면 그 연결을 정리하고 false */
    private boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 전송 실패로 연결 정리: {}", e.getMessage());
            emitter.completeWithError(e);
            return false;
        }
    }

    private void remove(Long emergencyId, SseEmitter emitter) {
        emitters.computeIfPresent(emergencyId, (id, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
