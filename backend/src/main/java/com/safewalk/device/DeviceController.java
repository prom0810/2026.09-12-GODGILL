package com.safewalk.device;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 푸시를 받을 기기(FCM 토큰) 등록·해제. 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 * 규칙은 {@link DeviceService} 참고.
 *
 *  - PUT    /api/users/me/devices   { "fcmToken": "...", "deviceType": "ANDROID" }  로그인 직후·토큰 갱신 시
 *  - DELETE /api/users/me/devices   { "fcmToken": "..." }                           로그아웃 시
 */
@RestController
@RequestMapping("/api/users/me/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PutMapping
    public ApiResponse<DeviceResponse> register(@LoginUser Long userId,
                                                @RequestBody(required = false) DeviceRequests.Register request) {
        return ApiResponse.ok(deviceService.register(userId, request));
    }

    @DeleteMapping
    public ApiResponse<Void> unregister(@LoginUser Long userId,
                                        @RequestBody(required = false) DeviceRequests.Unregister request) {
        deviceService.unregister(userId, request);
        return ApiResponse.ok();
    }
}
