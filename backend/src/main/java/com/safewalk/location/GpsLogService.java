package com.safewalk.location;

import com.safewalk.emergency.EmergencyEventRepository;
import com.safewalk.emergency.EmergencySseHub;
import com.safewalk.emergency.LocationPoint;
import com.safewalk.emergency.LocationStore;
import com.safewalk.global.AfterCommit;
import com.safewalk.guardian.GuardianRepository;
import com.safewalk.guardian.RegistrationStatus;
import com.safewalk.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class GpsLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GpsLogService.class);
    private final RedisGpsStore store;
    private final SosLocationLogRepository sosLogs;
    private final UserService users;
    private final GuardianRepository guardians;
    private final EmergencyEventRepository emergencies;
    private final LocationStore emergencyLocations;
    private final EmergencySseHub emergencySse;
    private final LocationSseHub locationSse;

    public GpsLogService(RedisGpsStore store, SosLocationLogRepository sosLogs, UserService users, GuardianRepository guardians,
                         EmergencyEventRepository emergencies, LocationStore emergencyLocations,
                         EmergencySseHub emergencySse, LocationSseHub locationSse) {
        this.store = store;
        this.sosLogs = sosLogs;
        this.users = users;
        this.guardians = guardians;
        this.emergencies = emergencies;
        this.emergencyLocations = emergencyLocations;
        this.emergencySse = emergencySse;
        this.locationSse = locationSse;
    }

    @Transactional(readOnly = true)
    public String start(Long userId) {
        users.getActiveUser(userId);
        return store.start(userId);
    }

    @Transactional
    public boolean append(Long userId, String sessionId, GpsRequest request) {
        users.getActiveUser(userId);
        validate(request);
        GpsPoint point = new GpsPoint(userId, sessionId, request.lat(), request.lng(),
                request.recordedAt(), request.accuracy());
        var open = emergencies.findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(
                userId, EmergencyEventRepository.OPEN_STATUSES);
        int result;
        if (open.isPresent()) {
            try {
                result = store.appendLive(point);
            } catch (DataAccessException e) {
                // The authenticated SOS owner can still upload a durable fix without Redis.
                LOGGER.warn("Redis latest fix unavailable for SOS {}", open.get().getEmergencyId(), e);
                result = 2;
            }
        } else {
            result = store.append(point);
        }
        // A new SOS may have committed while the Redis write was in flight.
        if (open.isEmpty()) {
            open = emergencies.findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(
                    userId, EmergencyEventRepository.OPEN_STATUSES);
        }
        if (result > 0 && open.isPresent()) {
            sosLogs.save(open.get().getEmergencyId(), point);
        }
        if (result == 2) {
            var publishedEmergency = open;
            AfterCommit.run(() -> {
                locationSse.publish(point);
                publishedEmergency.ifPresent(event -> {
                    LocationPoint emergencyPoint = new LocationPoint(point.lat(), point.lng(), point.accuracy(),
                            OffsetDateTime.ofInstant(point.recordedAt(), ZoneOffset.UTC));
                    if (emergencyLocations.update(event.getEmergencyId(), emergencyPoint, Instant.now())) {
                        emergencySse.publishLocation(event.getEmergencyId(), emergencyPoint);
                    }
                });
            });
        }
        return result > 0;
    }

    public void close(Long userId, String sessionId) {
        users.getActiveUser(userId);
        store.close(userId, sessionId);
        if (emergencies.findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(
                userId, EmergencyEventRepository.OPEN_STATUSES).isEmpty()) {
            locationSse.stopped(userId);
        }
    }

    @Transactional(readOnly = true)
    public GpsPoint latest(Long viewerId, Long userId) {
        checkView(viewerId, userId);
        return store.latest(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<GpsPoint> range(Long viewerId, Long userId, String sessionId, Instant from, Instant to) {
        checkView(viewerId, userId);
        if (from == null || to == null || from.isAfter(to) || Duration.between(from, to).compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("from/to는 순서대로 지정하고 조회 범위는 최대 60분이어야 합니다.");
        }
        List<GpsPoint> dbPoints = sosLogs.range(userId, sessionId, from, to);
        List<GpsPoint> points = new ArrayList<>(dbPoints);
        try {
            for (GpsPoint point : store.range(userId, sessionId, from, to)) {
                if (points.stream().noneMatch(saved ->
                        saved.recordedAt().toEpochMilli() == point.recordedAt().toEpochMilli())) {
                    points.add(point);
                }
            }
        } catch (LocationNotFoundException | DataAccessException e) {
            if (points.isEmpty()) throw e;
        }
        points.sort(Comparator.comparing(GpsPoint::recordedAt));
        return points;
    }

    @Transactional(readOnly = true)
    public SseEmitter stream(Long viewerId, Long userId) {
        checkView(viewerId, userId);
        return locationSse.subscribe(viewerId, userId, store.latest(userId).orElse(null));
    }

    /** Store SOS uploads durably, while keeping Redis's latest fix for the existing live API. */
    public void recordEmergency(Long userId, Long emergencyId, LocationPoint location) {
        GpsPoint point = new GpsPoint(userId, "sos-" + emergencyId, location.lat(), location.lng(),
                location.recordedAt().toInstant(), location.accuracy());
        sosLogs.save(emergencyId, point);
        AfterCommit.run(() -> {
            try {
                if (store.updateEmergencyLatest(point) == 2) locationSse.publish(point);
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to update Redis latest fix for SOS {}", emergencyId, e);
            }
        });
    }

    /** Copy the current Redis sharing history to the SOS record. Safe to retry. */
    public void backfillRecent(Long userId, Long emergencyId) {
        List<GpsPoint> recent;
        try {
            recent = store.recent(userId);
        } catch (RuntimeException e) {
            LOGGER.warn("Redis history unavailable for SOS {}", emergencyId, e);
            return;
        }
        if (!recent.isEmpty()) sosLogs.saveAll(emergencyId, recent);
    }

    /** Runs the second snapshot in a new transaction after the SOS has committed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void backfillRecentCommitted(Long userId, Long emergencyId) {
        backfillRecent(userId, emergencyId);
    }

    public Optional<LocationPoint> latestEmergency(Long emergencyId) {
        return sosLogs.latest(emergencyId).map(point -> new LocationPoint(
                point.lat(), point.lng(), point.accuracy(),
                OffsetDateTime.ofInstant(point.recordedAt(), ZoneOffset.UTC)));
    }

    public void closeEmergency(Long userId, Long emergencyId) {
        store.closeEmergency(userId, emergencyId);
        if (!store.hasActiveSession(userId)) locationSse.stopped(userId);
    }

    private void checkView(Long viewerId, Long userId) {
        users.getActiveUser(viewerId);
        if (viewerId.equals(userId)) return;
        boolean allowed = guardians.findFirstByUser_UserIdAndGuardianUser_UserIdAndRegistrationStatusAndDeletedAtIsNull(
                userId, viewerId, RegistrationStatus.CONNECTED)
                .filter(g -> g.isCanViewLocation() && g.getUser().getDeletedAt() == null)
                .isPresent();
        if (!allowed) throw new LocationNotFoundException();
    }

    private void validate(GpsRequest request) {
        if (request == null || request.lat() == null || request.lng() == null || request.recordedAt() == null
                || !Double.isFinite(request.lat()) || !Double.isFinite(request.lng())
                || request.lat() < -90 || request.lat() > 90 || request.lng() < -180 || request.lng() > 180
                || (request.accuracy() != null && (!Double.isFinite(request.accuracy()) || request.accuracy() < 0))) {
            throw new IllegalArgumentException("GPS 좌표, 측정 시각 또는 정확도가 올바르지 않습니다.");
        }
        if (request.recordedAt().isAfter(Instant.now().plusSeconds(60))) {
            throw new IllegalArgumentException("GPS 측정 시각이 미래입니다.");
        }
    }
}
