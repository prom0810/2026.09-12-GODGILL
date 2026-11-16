package com.safewalk.emergency;

import com.safewalk.emergency.EmergencyResponses.Role;
import com.safewalk.global.AfterCommit;
import com.safewalk.guardian.Guardian;
import com.safewalk.guardian.GuardianRepository;
import com.safewalk.guardian.RegistrationStatus;
import com.safewalk.location.GpsLogService;
import com.safewalk.notification.NotificationService;
import com.safewalk.notification.NotificationType;
import com.safewalk.notification.ReferenceType;
import com.safewalk.sensor.SensorContextResolver;
import com.safewalk.sensor.BpmLogWriter;
import com.safewalk.sensor.SensorStreamService;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SOS 발생 → 보호자 알림 → 실시간 위치 공유 → 종료.
 *
 * 흐름
 *  1. 사용자 폰이 SOS 시작 (10초 확인 팝업은 앱에서만, 취소하면 서버 호출 없음)
 *  2. 앱 연결된(CONNECTED) 보호자 전원에게 알림 기록 + FCM 푸시
 *     대표 보호자가 앱에 연결돼 있지 않으면 그 1명에게만 문자(MVP: 서버 로그)
 *  3. 사용자 폰이 5초마다 위치 전송 → 서버 메모리에 최신 1개 + Redis에 최근 이력 → SSE로 보호자 앱에 즉시 전달
 *  4. 사용자(RESOLVED/FALSE_ALARM/CANCELLED) 또는 보호자(RESOLVED)가 종료 → SSE 종료, 종료 알림
 *  5. 위치 끊김·자동 종료는 {@link EmergencyWatchdog}
 *
 * 워치 감지 SOS (경로 안내 중)
 *  - 워치가 낙상·심박 이상을 감지 → 폰 팝업 무응답 → type=FALL/ABNORMAL_HEART_RATE, source=WEARABLE로 시작.
 *    wearable·route·segment는 서버가 정한다 ({@link SensorContextResolver})
 *  - 심박 이상 SOS만: 시작 시 감지 당시 심박(bpm)을 Redis에 기록, SOS 동안 워치 심박을 받아 최신 값 갱신
 *    ({@link SensorStreamService}), 종료 시 발생·종료 심박을 bpm_log에 저장 ({@link BpmLogWriter})
 *  - 낙상은 사건 자체(emergency_event)만 기록하고 센서 값은 저장하지 않는다
 *
 * 권한
 *  - 당사자(SELF): 자기 사건 전부
 *  - 보호자(GUARDIAN): 당사자의 보호자 행 중 내 계정과 CONNECTED인 것이 있을 때. 위치는 can_view_location=true일 때만
 *  - 그 외: 404 (사건이 있는지도 알려 주지 않음)
 *
 * 알림·SSE·문자는 DB 커밋 뒤에 나간다 (실패한 요청의 알림이 가지 않게).
 */
@Service
public class EmergencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmergencyService.class);

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    /** 폰 시계가 앞서 있어도 이 정도까지는 그대로 받고, 더 미래면 서버 시각으로 바꾼다 */
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(1);

    private final EmergencyEventRepository eventRepository;
    private final EmergencyEventLogRepository logRepository;
    private final GuardianRepository guardianRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final LocationStore locationStore;
    private final EmergencySseHub sseHub;
    private final GpsLogService gpsLogService;
    private final SensorContextResolver sensorContextResolver;
    private final SensorStreamService sensorStreamService;
    private final BpmLogWriter bpmLogWriter;

    public EmergencyService(EmergencyEventRepository eventRepository, EmergencyEventLogRepository logRepository,
                            GuardianRepository guardianRepository, UserService userService,
                            NotificationService notificationService, LocationStore locationStore,
                            EmergencySseHub sseHub, GpsLogService gpsLogService,
                            SensorContextResolver sensorContextResolver, SensorStreamService sensorStreamService,
                            BpmLogWriter bpmLogWriter) {
        this.eventRepository = eventRepository;
        this.logRepository = logRepository;
        this.guardianRepository = guardianRepository;
        this.userService = userService;
        this.notificationService = notificationService;
        this.locationStore = locationStore;
        this.sseHub = sseHub;
        this.gpsLogService = gpsLogService;
        this.sensorContextResolver = sensorContextResolver;
        this.sensorStreamService = sensorStreamService;
        this.bpmLogWriter = bpmLogWriter;
    }

    // ── 1. 시작 ──────────────────────────────────────────────────────────

    /** POST /api/emergencies */
    @Transactional
    public EmergencyResponses.Started start(Long userId, EmergencyRequests.Start request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 없습니다.");
        }
        EmergencyType type = request.type() == null ? EmergencyType.SOS : request.type();
        if (request.source() == null || request.source() == EmergencySource.SYSTEM) {
            throw new IllegalArgumentException("source는 MOBILE 또는 WEARABLE이어야 합니다.");
        }
        if (type != EmergencyType.SOS && request.source() != EmergencySource.WEARABLE) {
            throw new IllegalArgumentException("낙상·심박 이상 SOS는 source=WEARABLE로 보내 주세요.");
        }
        if (request.wearableId() != null || request.routeId() != null || request.segmentId() != null) {
            throw new IllegalArgumentException("wearableId·routeId·segmentId는 서버가 정합니다. 빼고 보내 주세요.");
        }
        if ((request.lat() == null) != (request.lng() == null)) {
            throw new IllegalArgumentException("lat과 lng는 함께 보내거나 둘 다 생략해야 합니다.");
        }
        if (request.lat() != null) {
            validateCoordinate(request.lat(), request.lng(), request.accuracy());
        }
        if (request.bpm() != null && (request.bpm() < 20 || request.bpm() > 250)) {
            throw new IllegalArgumentException("bpm은 20~250 사이여야 합니다.");
        }

        // 같은 사용자의 SOS 시작 요청이 동시에 와도 하나씩 처리되게 사용자 행을 잠근다
        User user = userService.getActiveUserForUpdate(userId);
        eventRepository.findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(
                        userId, EmergencyEventRepository.OPEN_STATUSES)
                .ifPresent(open -> {
                    throw new EmergencyAlreadyOpenException(open.getEmergencyId());
                });

        EmergencyEvent event;
        if (type == EmergencyType.SOS) {
            event = EmergencyEvent.sos(user, request.source());
        } else {
            SensorContextResolver.Result ctx = sensorContextResolver.resolve(userId, request.lat(), request.lng());
            if (ctx.context() == null) {
                throw new EmergencyStateException(switch (ctx.missing()) {
                    case NO_ROUTE -> "워치 감지 SOS는 경로 안내 중에만 보낼 수 있습니다.";
                    case NO_WEARABLE -> "연결된 워치가 없습니다. 워치 연결을 확인해 주세요.";
                    case NO_SEGMENT -> "진행 중 경로의 구간 정보가 없습니다.";
                });
            }
            event = EmergencyEvent.autoDetected(user, type, ctx.context().wearableId(), ctx.context().routeId(),
                    ctx.context().segmentId());
        }
        event = eventRepository.save(event);
        Long emergencyId = event.getEmergencyId();
        log(event, EmergencyLogType.CREATED,
                type == EmergencyType.SOS ? request.source().name() : request.source().name() + " " + type.name());

        // 심박 이상 SOS: 감지 당시 심박을 Redis에 기록 → 종료 때 bpm_log(발생·종료 심박)로 저장
        if (type == EmergencyType.ABNORMAL_HEART_RATE) {
            Integer startBpm = request.bpm();
            long occurredAtMs = event.getOccurredAt().toInstant().toEpochMilli();
            AfterCommit.run(() -> {
                try {
                    sensorStreamService.beginHeartRate(emergencyId, startBpm, occurredAtMs);
                } catch (RuntimeException e) {
                    LOGGER.warn("SOS 발생 당시 심박 기록 실패 (emergencyId={})", emergencyId, e);
                }
            });
        }

        gpsLogService.backfillRecent(userId, emergencyId);
        // A concurrent upload can finish while the SOS transaction is committing.
        AfterCommit.run(() -> {
            try {
                gpsLogService.backfillRecentCommitted(userId, emergencyId);
            } catch (RuntimeException e) {
                LOGGER.warn("SOS Redis history backfill failed (emergencyId={})", emergencyId, e);
            }
        });

        if (request.lat() != null) {
            LocationPoint first = new LocationPoint(request.lat(), request.lng(), request.accuracy(),
                    event.getOccurredAt());
            gpsLogService.recordEmergency(userId, emergencyId, first);
            AfterCommit.run(() -> {
                locationStore.update(emergencyId, first, Instant.now());
            });
        }

        // 앱 연결된 보호자 전원에게 알림
        List<Guardian> connected = connectedGuardians(userId);
        int locationShared = 0;
        for (Guardian g : connected) {
            boolean shared = g.isCanViewLocation();
            if (shared) {
                locationShared++;
            }
            notificationService.notify(g.getGuardianUser(), NotificationType.EMERGENCY, "EMERGENCY_STARTED",
                    "긴급 SOS",
                    startedText(user.getName(), type) + (shared ? " 지금 위치를 확인해 주세요." : ""),
                    ReferenceType.EMERGENCY_EVENT, emergencyId,
                    Map.of("emergencyId", String.valueOf(emergencyId),
                            "wardName", user.getName(),
                            "type", type.name(),
                            "locationShared", String.valueOf(shared)));
        }
        if (!connected.isEmpty()) {
            log(event, EmergencyLogType.GUARDIAN_PUSHED, connected.size() + "명 (위치 공유 " + locationShared + "명)");
        }

        // 대표 보호자가 앱에 연결돼 있지 않으면 그 1명에게만 문자
        int sms = smsPrimaryGuardian(event,
                "[안심동행] " + startedText(user.getName(), type) + " ("
                        + HH_MM.format(event.getOccurredAt().atZoneSameInstant(KOREA)) + ")");

        return new EmergencyResponses.Started(emergencyId, event.getStatus(), event.getOccurredAt(),
                new EmergencyResponses.Notified(connected.size(), locationShared, sms));
    }

    // ── 2. 위치 ──────────────────────────────────────────────────────────

    /** POST /api/emergencies/{id}/locations — 당사자만. 종료된 사건이면 409 (앱은 전송을 멈춘다) */
    @Transactional
    public void postLocation(Long userId, Long emergencyId, EmergencyRequests.Location request) {
        if (request == null || request.lat() == null || request.lng() == null) {
            throw new IllegalArgumentException("lat과 lng를 입력해 주세요.");
        }
        validateCoordinate(request.lat(), request.lng(), request.accuracy());
        userService.getActiveUser(userId);
        EmergencyEvent event = eventRepository.findById(emergencyId)
                .filter(e -> e.isOwnedBy(userId))
                .orElseThrow(EmergencyService::notFound);
        if (!event.isOpen()) {
            throw new EmergencyStateException("종료된 SOS입니다. 위치 전송을 멈춰 주세요.");
        }

        OffsetDateTime now = EmergencyEvent.now();
        OffsetDateTime recordedAt = request.recordedAt();
        if (recordedAt == null || recordedAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            recordedAt = now;
        }
        LocationPoint point = new LocationPoint(request.lat(), request.lng(), request.accuracy(), recordedAt);
        gpsLogService.recordEmergency(userId, emergencyId, point);
        AfterCommit.run(() -> {
            if (locationStore.update(emergencyId, point, Instant.now())) {
                sseHub.publishLocation(emergencyId, point);
            }
        });
    }

    /** GET /api/emergencies/{id}/locations/latest — 당사자 또는 위치 열람이 허용된 보호자. 없으면 null */
    @Transactional(readOnly = true)
    public LocationPoint latestLocation(Long accountId, Long emergencyId) {
        EmergencyEvent event = findViewable(accountId, emergencyId, true);
        return currentLocation(event).orElse(null);
    }

    /** GET /api/emergencies/{id}/stream — SSE. 종료된 사건이면 status 1건을 보내고 바로 닫는다 */
    @Transactional(readOnly = true)
    public SseEmitter stream(Long accountId, Long emergencyId) {
        EmergencyEvent event = findViewable(accountId, emergencyId, true);
        if (!event.isOpen()) {
            return sseHub.closedStream(new EmergencySseHub.StatusEvent(event.getStatus(), event.getResolvedAt()));
        }
        return sseHub.subscribe(emergencyId, currentLocation(event).orElse(null));
    }

    // ── 3. 종료 ──────────────────────────────────────────────────────────

    /** PATCH /api/emergencies/{id} */
    @Transactional
    public EmergencyResponses.Resolved resolve(Long accountId, Long emergencyId, EmergencyRequests.Resolve request) {
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("status를 입력해 주세요. (RESOLVED / FALSE_ALARM / CANCELLED)");
        }
        EmergencyStatus target = request.status();
        if (target.isOpen()) {
            throw new IllegalArgumentException("status는 RESOLVED, FALSE_ALARM, CANCELLED 중 하나여야 합니다.");
        }
        User me = userService.getActiveUser(accountId);
        EmergencyEvent event = eventRepository.findByIdForUpdate(emergencyId).orElseThrow(EmergencyService::notFound);
        Access access = access(accountId, event).orElseThrow(EmergencyService::notFound);
        if (access.role() == Role.GUARDIAN && target != EmergencyStatus.RESOLVED) {
            throw new IllegalArgumentException("보호자는 RESOLVED로만 종료할 수 있습니다.");
        }

        event.resolve(target, me);
        log(event, EmergencyLogType.valueOf(target.name()),
                access.role() == Role.SELF ? "본인 종료" : "보호자 종료 (guardianId=" + access.guardian().getGuardianId() + ")");
        afterClosed(event, access.role() == Role.GUARDIAN ? access.guardian() : null);

        return new EmergencyResponses.Resolved(emergencyId, event.getStatus(), event.getResolvedAt(),
                new EmergencyResponses.ResolvedBy(accountId, access.role()));
    }

    /** {@link EmergencyWatchdog}가 호출: 오래 열려 있는 사건 자동 종료 */
    @Transactional
    public void autoClose(Long emergencyId) {
        Optional<EmergencyEvent> found = eventRepository.findByIdForUpdate(emergencyId);
        if (found.isEmpty() || !found.get().isOpen()) {
            return;
        }
        EmergencyEvent event = found.get();
        event.autoClose();
        log(event, EmergencyLogType.AUTO_CLOSED, null);
        afterClosed(event, null);
    }

    /** {@link EmergencyWatchdog}가 호출: 위치가 오래 안 들어와 보호자에게 알림 */
    @Transactional
    public void alertLocationLost(Long emergencyId, OffsetDateTime lastRecordedAt) {
        Optional<EmergencyEvent> found = eventRepository.findById(emergencyId);
        if (found.isEmpty() || !found.get().isOpen()) {
            return;
        }
        EmergencyEvent event = found.get();
        User ward = event.getUser();
        List<Guardian> targets = connectedGuardians(ward.getUserId()).stream()
                .filter(Guardian::isCanViewLocation)
                .toList();
        String when = HH_MM.format(lastRecordedAt.atZoneSameInstant(KOREA));
        for (Guardian g : targets) {
            notificationService.notify(g.getGuardianUser(), NotificationType.EMERGENCY, "EMERGENCY_LOCATION_LOST",
                    "위치 수신 끊김",
                    ward.getName() + "님의 위치가 " + when + " 이후 들어오지 않고 있어요. 마지막 위치를 확인해 주세요.",
                    ReferenceType.EMERGENCY_EVENT, emergencyId,
                    Map.of("emergencyId", String.valueOf(emergencyId)));
        }
        log(event, EmergencyLogType.LOCATION_LOST, "마지막 위치 " + when + ", 알림 " + targets.size() + "명");
    }

    /** 종료 공통: SSE 닫기, 위치 삭제, 종료 알림·문자 */
    private void afterClosed(EmergencyEvent event, Guardian closedByGuardian) {
        Long emergencyId = event.getEmergencyId();
        User ward = event.getUser();
        EmergencySseHub.StatusEvent status = new EmergencySseHub.StatusEvent(event.getStatus(), event.getResolvedAt());
        boolean heartRate = event.getType() == EmergencyType.ABNORMAL_HEART_RATE;
        AfterCommit.run(() -> {
            sseHub.publishStatusAndClose(emergencyId, status);
            locationStore.remove(emergencyId);
            try {
                gpsLogService.closeEmergency(ward.getUserId(), emergencyId);
            } catch (RuntimeException e) {
                LOGGER.warn("종료된 SOS 최신 위치 정리 실패 (emergencyId={})", emergencyId, e);
            }
            // 심박 이상 SOS: 발생 당시·종료 시 심박 → bpm_log
            if (heartRate) {
                try {
                    bpmLogWriter.save(emergencyId);
                } catch (RuntimeException e) {
                    LOGGER.warn("bpm_log 저장 실패 (emergencyId={})", emergencyId, e);
                }
            }
        });

        String content = switch (event.getStatus()) {
            case FALSE_ALARM -> ward.getName() + "님이 SOS를 오작동으로 종료했어요.";
            case CANCELLED -> event.getResolvedBy() == null
                    ? ward.getName() + "님의 SOS가 오래 지나 자동으로 종료되었어요."
                    : ward.getName() + "님이 SOS를 취소했어요.";
            default -> closedByGuardian == null
                    ? ward.getName() + "님의 SOS 상황이 종료되었어요."
                    : closedByGuardian.getName() + "님이 " + ward.getName() + "님의 SOS 상황을 종료했어요.";
        };
        Map<String, String> data = Map.of(
                "emergencyId", String.valueOf(emergencyId),
                "status", event.getStatus().name());

        for (Guardian g : connectedGuardians(ward.getUserId())) {
            if (closedByGuardian != null && g.getGuardianId().equals(closedByGuardian.getGuardianId())) {
                continue; // 직접 종료한 보호자에게는 보내지 않음
            }
            notificationService.notify(g.getGuardianUser(), NotificationType.EMERGENCY, "EMERGENCY_ENDED",
                    "SOS 종료", content, ReferenceType.EMERGENCY_EVENT, emergencyId, data);
        }
        // 당사자가 직접 끝낸 게 아니면 당사자에게도 알린다 (앱이 위치 전송을 멈추도록)
        if (event.getResolvedBy() == null || closedByGuardian != null) {
            notificationService.notify(ward, NotificationType.EMERGENCY, "EMERGENCY_ENDED",
                    "SOS 종료", content, ReferenceType.EMERGENCY_EVENT, emergencyId, data);
        }
        smsPrimaryGuardian(event, "[안심동행] " + ward.getName() + "님의 SOS 상황이 종료되었습니다.");
    }

    // ── 4. 조회 ──────────────────────────────────────────────────────────

    /** GET /api/emergencies/active — 내 진행 중 SOS. 없으면 null */
    @Transactional(readOnly = true)
    public EmergencyResponses.Detail active(Long userId) {
        userService.getActiveUser(userId);
        return eventRepository.findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(
                        userId, EmergencyEventRepository.OPEN_STATUSES)
                .map(e -> detail(e, new Access(Role.SELF, null)))
                .orElse(null);
    }

    /** GET /api/emergencies/{id} */
    @Transactional(readOnly = true)
    public EmergencyResponses.Detail get(Long accountId, Long emergencyId) {
        userService.getActiveUser(accountId);
        EmergencyEvent event = eventRepository.findById(emergencyId).orElseThrow(EmergencyService::notFound);
        Access access = access(accountId, event).orElseThrow(EmergencyService::notFound);
        return detail(event, access);
    }

    /** GET /api/emergencies/shared — 내가 보호 중인 사람들의 진행 중 SOS (푸시를 놓쳤을 때 앱 시작 시 확인) */
    @Transactional(readOnly = true)
    public List<EmergencyResponses.Shared> shared(Long accountId) {
        userService.getActiveUser(accountId);
        Map<Long, Guardian> byWard = guardianRepository
                .findByGuardianUser_UserIdAndRegistrationStatusAndDeletedAtIsNullOrderByConnectedAtAsc(
                        accountId, RegistrationStatus.CONNECTED)
                .stream()
                .filter(g -> g.getUser().getDeletedAt() == null && !g.getUser().getUserId().equals(accountId))
                .collect(Collectors.toMap(g -> g.getUser().getUserId(), Function.identity(), (a, b) -> a));
        if (byWard.isEmpty()) {
            return List.of();
        }
        return eventRepository.findByUser_UserIdInAndStatusInOrderByOccurredAtDesc(
                        byWard.keySet(), EmergencyEventRepository.OPEN_STATUSES)
                .stream()
                .map(e -> {
                    Guardian g = byWard.get(e.getUser().getUserId());
                    return new EmergencyResponses.Shared(e.getEmergencyId(), g.getGuardianId(),
                            e.getUser().getUserId(), e.getUser().getName(), e.getType(), e.getSource(),
                            e.getOccurredAt(), g.isCanViewLocation());
                })
                .toList();
    }

    // ── 내부 ────────────────────────────────────────────────────────────

    /** 요청한 계정의 입장. 보호자면 그 보호자 행을 함께 담는다 */
    private record Access(Role role, Guardian guardian) {
        boolean canViewLocation() {
            return role == Role.SELF || guardian.isCanViewLocation();
        }
    }

    private Optional<Access> access(Long accountId, EmergencyEvent event) {
        if (event.isOwnedBy(accountId)) {
            return Optional.of(new Access(Role.SELF, null));
        }
        return guardianRepository
                .findFirstByUser_UserIdAndGuardianUser_UserIdAndRegistrationStatusAndDeletedAtIsNull(
                        event.getUser().getUserId(), accountId, RegistrationStatus.CONNECTED)
                .map(g -> new Access(Role.GUARDIAN, g));
    }

    /** 볼 수 있는 사건만 돌려준다. needLocation이면 위치 열람 권한까지 확인 (없으면 404) */
    private EmergencyEvent findViewable(Long accountId, Long emergencyId, boolean needLocation) {
        userService.getActiveUser(accountId);
        EmergencyEvent event = eventRepository.findById(emergencyId).orElseThrow(EmergencyService::notFound);
        Access access = access(accountId, event).orElseThrow(EmergencyService::notFound);
        if (needLocation && !access.canViewLocation()) {
            throw notFound();
        }
        return event;
    }

    /** Fall back to the durable SOS log after a server restart. */
    private Optional<LocationPoint> currentLocation(EmergencyEvent event) {
        if (!event.isOpen()) {
            return Optional.empty();
        }
        Optional<LocationPoint> latest = locationStore.latest(event.getEmergencyId());
        if (latest.isPresent()) {
            return latest;
        }
        // sos_log에는 SOS 시작 위치도 들어 있으므로 별도의 발생 위치 대체값은 필요 없다
        return gpsLogService.latestEmergency(event.getEmergencyId());
    }

    private EmergencyResponses.Detail detail(EmergencyEvent e, Access access) {
        // 발생 위치 = sos_log의 SOS 세션('sos-{id}') 첫 위치 (emergency_event에는 위치가 없음, 2026-11-13)
        EmergencyResponses.InitialLocation initial = access.canViewLocation()
                ? gpsLogService.initialEmergency(e.getEmergencyId())
                        .map(p -> new EmergencyResponses.InitialLocation(p.lat(), p.lng()))
                        .orElse(null)
                : null;
        EmergencyResponses.ResolvedBy resolvedBy = null;
        if (e.getResolvedBy() != null) {
            Long by = e.getResolvedBy().getUserId();
            resolvedBy = new EmergencyResponses.ResolvedBy(by, e.isOwnedBy(by) ? Role.SELF : Role.GUARDIAN);
        }
        return new EmergencyResponses.Detail(e.getEmergencyId(), e.getUser().getUserId(), e.getUser().getName(),
                e.getType(), e.getSource(), e.getStatus(), e.getOccurredAt(), e.getResolvedAt(), resolvedBy,
                initial, access.role(), access.canViewLocation());
    }

    /** 앱 계정으로 연결된 보호자 (보호자 계정이 탈퇴했으면 제외) */
    private List<Guardian> connectedGuardians(Long wardId) {
        return guardianRepository.findByUser_UserIdAndDeletedAtIsNullOrderByGuardianIdAsc(wardId).stream()
                .filter(g -> g.getRegistrationStatus() == RegistrationStatus.CONNECTED)
                .filter(g -> g.getGuardianUser() != null && g.getGuardianUser().getDeletedAt() == null)
                .toList();
    }

    /**
     * 대표 보호자가 앱에 연결돼 있지 않으면 그 1명에게만 문자 (MVP: 서버 로그).
     * 보호자 확인(인증번호) 기능이 생기면 여기서 "인증된 보호자" 조건을 추가한다.
     *
     * @return 보낸 문자 수 (0 또는 1)
     */
    private int smsPrimaryGuardian(EmergencyEvent event, String text) {
        Optional<Guardian> primary = guardianRepository
                .findByUser_UserIdAndPrimaryTrueAndDeletedAtIsNull(event.getUser().getUserId())
                .filter(g -> g.getRegistrationStatus() != RegistrationStatus.CONNECTED);
        if (primary.isEmpty()) {
            return 0;
        }
        notificationService.sendSms(primary.get().getPhone(), text);
        log(event, EmergencyLogType.GUARDIAN_SMS_SENT, "대표 보호자 guardianId=" + primary.get().getGuardianId());
        return 1;
    }

    /** 시작 알림·문자 문구. 자동 감지면 무엇이 감지됐는지 알린다 */
    private static String startedText(String name, EmergencyType type) {
        return switch (type) {
            case FALL -> name + "님의 워치에서 낙상이 감지되어 SOS가 시작됐어요.";
            case ABNORMAL_HEART_RATE -> name + "님의 워치에서 심박 이상이 감지되어 SOS가 시작됐어요.";
            default -> name + "님이 SOS를 요청했어요.";
        };
    }

    private void log(EmergencyEvent event, EmergencyLogType type, String message) {
        logRepository.save(new EmergencyEventLog(event, type, message));
    }

    private static void validateCoordinate(Double lat, Double lng, Double accuracy) {
        if (lat.isNaN() || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat은 -90~90 사이여야 합니다.");
        }
        if (lng.isNaN() || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("lng는 -180~180 사이여야 합니다.");
        }
        if (accuracy != null && (accuracy.isNaN() || accuracy < 0)) {
            throw new IllegalArgumentException("accuracy는 0 이상이어야 합니다.");
        }
    }

    private static EmergencyNotFoundException notFound() {
        return new EmergencyNotFoundException("SOS를 찾을 수 없습니다.");
    }
}
