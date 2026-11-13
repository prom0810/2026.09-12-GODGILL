package com.safewalk.wearable;

import com.safewalk.route.RouteRepository;
import com.safewalk.route.RouteStatus;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웨어러블 기기 등록·연결·조회·삭제.
 *
 * 등록 (PUT /api/wearables)
 *  - 같은 계정에 같은 deviceIdentifier가 있으면 새로 만들지 않고 이름만 갱신한다
 *  - 같은 워치를 다른 계정에 등록하는 것은 허용한다. 등록만으로는 연결 상태가 바뀌지 않는다
 *
 * 연결 (PATCH /api/wearables/{id}/connection, connected=true)
 *  - 이미 연결 중이면 마지막 연결 시각만 갱신 (앱 재시작·경로 안내 시작 때 확인용)
 *  - 이 워치를 다른 계정에서 연결 중 → 409 IN_USE_BY_OTHER_ACCOUNT (상대 계정 정보는 알려 주지 않음).
 *    단, 그 연결이 {@link WearableProperties#connectionStaleHours()} 넘게 바뀌지 않았고 상대가 경로 안내 중이
 *    아니면 갱신이 멈춘 연결로 보고 해제한 뒤 연결한다
 *  - 내 다른 워치가 연결 중
 *      경로 안내 중 → 409 GUIDANCE_IN_PROGRESS (전환 불가)
 *      안내 중 아님 → 409 SWITCH_REQUIRED (+ 기존 기기 정보). 사용자가 확인하면 replaceCurrent=true로 다시 요청
 *  - 사용자당 연결 1대(uq_wearable_one_active), 워치당 연결 1계정(uq_wearable_identifier_connected)은 DB가 최종 보장한다.
 *    동시에 같은 워치를 연결하면 늦은 쪽이 인덱스에 걸려 409 IN_USE_BY_OTHER_ACCOUNT
 *
 * 해제 (connected=false): 언제든 가능 (경로 안내 중이어도, 워치가 꺼져 있어도). 블루투스 연결이 끊겼을 때도 앱이 호출한다
 *
 * last_connected_at은 연결 상태가 바뀔 때(연결·해제·연결 확인)만 갱신한다.
 * 등록·연결·해제·삭제는 사용자 행을 잠그고 처리한다.
 */
@Service
public class WearableService {

    private final WearableDeviceRepository wearableRepository;
    private final RouteRepository routeRepository;
    private final UserService userService;
    private final WearableProperties properties;

    public WearableService(WearableDeviceRepository wearableRepository, RouteRepository routeRepository,
                           UserService userService, WearableProperties properties) {
        this.wearableRepository = wearableRepository;
        this.routeRepository = routeRepository;
        this.userService = userService;
        this.properties = properties;
    }

    /** PUT /api/wearables */
    @Transactional
    public WearableResponse register(Long userId, WearableRequests.Register request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 없습니다.");
        }
        WearableType type = WearableType.from(request.deviceType());
        String identifier = requireText(request.deviceIdentifier(), WearableDevice.IDENTIFIER_MAX, "deviceIdentifier");
        String name = optionalText(request.deviceName(), WearableDevice.NAME_MAX, "deviceName");

        User user = userService.getActiveUserForUpdate(userId);
        Optional<WearableDevice> existing =
                wearableRepository.findByUser_UserIdAndDeviceIdentifierAndDeletedAtIsNull(userId, identifier);
        if (existing.isPresent()) {
            WearableDevice device = existing.get();
            if (device.getDeviceType() != type) {
                throw new IllegalArgumentException("이미 다른 종류(" + device.getDeviceType()
                        + ")로 등록된 기기입니다. 삭제한 뒤 다시 등록해 주세요.");
            }
            device.rename(name);
            return WearableResponse.from(device);
        }
        return WearableResponse.from(wearableRepository.save(new WearableDevice(user, type, identifier, name)));
    }

    /** GET /api/wearables — 최근 연결 상태 변경 순 */
    @Transactional(readOnly = true)
    public List<WearableResponse> list(Long userId) {
        userService.getActiveUser(userId);
        return wearableRepository.findByUser_UserIdAndDeletedAtIsNullOrderByLastConnectedAtDesc(userId).stream()
                .map(WearableResponse::from)
                .toList();
    }

    /** PATCH /api/wearables/{wearableId}/connection */
    @Transactional
    public WearableResponse.Connection updateConnection(Long userId, Long wearableId,
                                                        WearableRequests.Connection request) {
        if (request == null || request.connected() == null) {
            throw new IllegalArgumentException("connected(true 또는 false)를 입력해 주세요.");
        }
        userService.getActiveUserForUpdate(userId);
        WearableDevice device = findOwned(userId, wearableId);
        OffsetDateTime now = OffsetDateTime.now();

        if (!request.connected()) {
            device.disconnect(now);
            return new WearableResponse.Connection(WearableResponse.from(device), null);
        }
        if (device.isActive()) {
            device.connect(now);
            return new WearableResponse.Connection(WearableResponse.from(device), null);
        }

        // 1) 다른 계정에서 연결 중인지 먼저 본다 (안 되는 연결이면 전환 확인을 묻지 않게)
        Optional<WearableDevice> otherAccount = wearableRepository
                .findFirstByDeviceIdentifierAndActiveTrueAndUser_UserIdNot(device.getDeviceIdentifier(), userId);
        OffsetDateTime staleCutoff = now.minus(Duration.ofHours(properties.connectionStaleHours()));
        if (otherAccount.isPresent() && !isReleasable(otherAccount.get(), staleCutoff)) {
            throw new WearableConnectionException(WearableConnectionException.Reason.IN_USE_BY_OTHER_ACCOUNT);
        }

        // 2) 내 다른 워치가 연결 중인지
        Optional<WearableDevice> current = wearableRepository.findByUser_UserIdAndActiveTrueAndDeletedAtIsNull(userId)
                .stream()
                .filter(d -> !d.getWearableId().equals(device.getWearableId()))
                .findFirst();
        if (current.isPresent()) {
            WearableResponse currentResponse = WearableResponse.from(current.get());
            if (routeRepository.existsByUser_UserIdAndStatus(userId, RouteStatus.IN_PROGRESS)) {
                throw new WearableConnectionException(
                        WearableConnectionException.Reason.GUIDANCE_IN_PROGRESS, currentResponse);
            }
            if (!Boolean.TRUE.equals(request.replaceCurrent())) {
                throw new WearableConnectionException(
                        WearableConnectionException.Reason.SWITCH_REQUIRED, currentResponse);
            }
        }

        // 3) 바꾸기: 다른 계정의 만료된 연결 해제 → 내 기존 워치 해제 → 이 워치 연결
        if (otherAccount.isPresent()
                && wearableRepository.releaseStaleConnection(otherAccount.get().getWearableId(), staleCutoff, now) == 0) {
            // 확인한 뒤 상대가 연결을 갱신했거나 안내를 시작함
            throw new WearableConnectionException(WearableConnectionException.Reason.IN_USE_BY_OTHER_ACCOUNT);
        }
        Long disconnectedId = null;
        if (current.isPresent()) {
            current.get().disconnect(now);
            disconnectedId = current.get().getWearableId();
            // 해제를 먼저 DB에 반영해야 uq_wearable_one_active에 걸리지 않는다
            wearableRepository.flush();
        }
        device.connect(now);
        try {
            wearableRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 같은 워치를 다른 계정이 동시에 연결함 (uq_wearable_identifier_connected)
            throw new WearableConnectionException(WearableConnectionException.Reason.IN_USE_BY_OTHER_ACCOUNT);
        }
        return new WearableResponse.Connection(WearableResponse.from(device), disconnectedId);
    }

    /** DELETE /api/wearables/{wearableId} — soft delete(연결도 해제) */
    @Transactional
    public void delete(Long userId, Long wearableId) {
        userService.getActiveUserForUpdate(userId);
        findOwned(userId, wearableId).delete(OffsetDateTime.now());
    }

    /** 다른 계정의 연결이 만료됐는지: 연결 상태가 cutoff 이전에 마지막으로 바뀌었고 그 계정이 안내 중이 아님 */
    private boolean isReleasable(WearableDevice other, OffsetDateTime cutoff) {
        OffsetDateTime last = other.getLastConnectedAt() != null ? other.getLastConnectedAt() : other.getConnectedAt();
        return last.isBefore(cutoff)
                && !routeRepository.existsByUser_UserIdAndStatus(other.getUser().getUserId(), RouteStatus.IN_PROGRESS);
    }

    private WearableDevice findOwned(Long userId, Long wearableId) {
        return wearableRepository.findByWearableIdAndUser_UserIdAndDeletedAtIsNull(wearableId, userId)
                .orElseThrow(() -> new WearableNotFoundException("웨어러블 기기를 찾을 수 없습니다."));
    }

    private static String requireText(String raw, int max, String name) {
        String value = optionalText(raw, max, name);
        if (value == null) {
            throw new IllegalArgumentException(name + "를 입력해 주세요.");
        }
        return value;
    }

    private static String optionalText(String raw, int max, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(name + "는 " + max + "자 이하여야 합니다.");
        }
        return trimmed;
    }
}
