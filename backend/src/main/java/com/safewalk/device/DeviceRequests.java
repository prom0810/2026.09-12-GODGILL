package com.safewalk.device;

/** 기기(FCM 토큰) API 요청 본문 */
public final class DeviceRequests {

    private DeviceRequests() {
    }

    /**
     * PUT /api/users/me/devices
     * { "fcmToken": "...", "deviceType": "ANDROID" }   deviceType은 선택 (ANDROID / WEAR_OS)
     */
    public record Register(String fcmToken, DeviceType deviceType) {
    }

    /**
     * DELETE /api/users/me/devices
     * { "fcmToken": "..." }
     */
    public record Unregister(String fcmToken) {
    }
}
