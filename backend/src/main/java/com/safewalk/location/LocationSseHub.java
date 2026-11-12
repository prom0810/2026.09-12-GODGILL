package com.safewalk.location;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import com.safewalk.guardian.GuardianRepository;
import com.safewalk.guardian.RegistrationStatus;
import com.safewalk.user.UserService;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Same event format as the existing emergency stream: `location` plus keepalive comments. */
@Component
public class LocationSseHub {
    private record Connection(Long viewerId, SseEmitter emitter) { }
    private final Map<Long, Set<Connection>> subscribers = new ConcurrentHashMap<>();
    private final GuardianRepository guardians;
    private final UserService users;

    public LocationSseHub(GuardianRepository guardians, UserService users) {
        this.guardians = guardians;
        this.users = users;
    }

    public SseEmitter subscribe(Long viewerId, Long userId, GpsPoint initial) {
        SseEmitter emitter = new SseEmitter(30 * 60_000L);
        Connection connection = new Connection(viewerId, emitter);
        subscribers.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(connection);
        Runnable cleanup = () -> remove(userId, connection);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        if (send(emitter, SseEmitter.event().comment("connected")) && initial != null) {
            send(emitter, SseEmitter.event().name("location").data(initial, MediaType.APPLICATION_JSON));
        }
        return emitter;
    }

    @Transactional(readOnly = true)
    public void publish(GpsPoint point) {
        Set<Connection> set = subscribers.get(point.userId());
        if (set != null) for (Connection connection : set) {
            if (!canView(connection.viewerId(), point.userId()) ||
                    !send(connection.emitter(), SseEmitter.event().name("location").data(point, MediaType.APPLICATION_JSON))) {
                connection.emitter().complete();
                remove(point.userId(), connection);
            }
        }
    }

    public void stopped(Long userId) {
        Set<Connection> set = subscribers.remove(userId);
        if (set != null) for (Connection connection : set) {
            send(connection.emitter(), SseEmitter.event().name("sharing_stopped")
                    .data(Map.of("status", "STOPPED"), MediaType.APPLICATION_JSON));
            connection.emitter().complete();
        }
    }

    @Scheduled(fixedDelay = 20_000)
    @Transactional(readOnly = true)
    public void ping() {
        subscribers.forEach((userId, set) -> {
            for (Connection connection : set) {
                if (!canView(connection.viewerId(), userId) ||
                        !send(connection.emitter(), SseEmitter.event().comment("ping"))) {
                    connection.emitter().complete();
                    remove(userId, connection);
                }
            }
        });
    }

    private boolean canView(Long viewerId, Long userId) {
        try {
            users.getActiveUser(viewerId);
            users.getActiveUser(userId);
            if (viewerId.equals(userId)) return true;
            return guardians.findFirstByUser_UserIdAndGuardianUser_UserIdAndRegistrationStatusAndDeletedAtIsNull(
                    userId, viewerId, RegistrationStatus.CONNECTED)
                    .filter(g -> g.isCanViewLocation()).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void remove(Long userId, Connection connection) {
        Set<Connection> set = subscribers.get(userId);
        if (set != null) {
            set.remove(connection);
            if (set.isEmpty()) subscribers.remove(userId, set);
        }
    }

    private boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try { emitter.send(event); return true; }
        catch (IOException | IllegalStateException e) { emitter.complete(); return false; }
    }
}
