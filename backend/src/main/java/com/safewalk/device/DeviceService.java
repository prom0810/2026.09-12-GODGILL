package com.safewalk.device;

import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FCM 토큰 등록·해제.
 *
 * 규칙
 *  - 앱은 로그인 직후와 FirebaseMessagingService.onNewToken(토큰 갱신) 때 등록을 호출한다.
 *  - 같은 토큰이 이미 있으면 새 행을 만들지 않고, 주인을 지금 로그인한 사용자로 바꾸고 다시 활성화한다
 *    (같은 폰에서 계정을 바꿔 로그인한 경우: 이전 계정에게 가던 알림이 더는 이 폰으로 오지 않는다).
 *  - 해제는 로그아웃 때 호출한다. 내 기기가 아니거나 없는 토큰이어도 성공으로 처리한다
 *    (로그아웃이 이 API 때문에 실패하면 안 되고, 다른 사람 토큰의 존재 여부도 알려 주지 않기 위함).
 */
@Service
public class DeviceService {

    private final UserDeviceRepository deviceRepository;
    private final UserService userService;

    public DeviceService(UserDeviceRepository deviceRepository, UserService userService) {
        this.deviceRepository = deviceRepository;
        this.userService = userService;
    }

    /** PUT /api/users/me/devices */
    @Transactional
    public DeviceResponse register(Long userId, DeviceRequests.Register request) {
        String token = normalizeToken(request == null ? null : request.fcmToken());
        User user = userService.getActiveUser(userId);

        Optional<UserDevice> existing = deviceRepository.findByFcmToken(token);
        UserDevice device;
        if (existing.isPresent()) {
            device = existing.get();
            device.assignTo(user, request.deviceType());
        } else {
            device = deviceRepository.save(new UserDevice(user, token, request.deviceType()));
        }
        return DeviceResponse.from(device);
    }

    /** DELETE /api/users/me/devices */
    @Transactional
    public void unregister(Long userId, DeviceRequests.Unregister request) {
        String token = normalizeToken(request == null ? null : request.fcmToken());
        userService.getActiveUser(userId);
        deviceRepository.findByFcmToken(token)
                .filter(device -> device.isOwnedBy(userId))
                .ifPresent(UserDevice::deactivate);
    }

    private static String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("fcmToken을 입력해 주세요.");
        }
        String trimmed = token.trim();
        if (trimmed.length() > UserDevice.MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("fcmToken이 너무 깁니다. (최대 " + UserDevice.MAX_TOKEN_LENGTH + "자)");
        }
        return trimmed;
    }
}
