package com.safewalk.device;

import java.time.OffsetDateTime;

/** 등록된 기기 정보. 토큰 전체는 돌려주지 않는다(앱이 이미 알고 있음). */
public record DeviceResponse(
        Long userDeviceId,
        DeviceType deviceType,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DeviceResponse from(UserDevice device) {
        return new DeviceResponse(
                device.getUserDeviceId(),
                device.getDeviceType(),
                device.isActive(),
                device.getCreatedAt(),
                device.getUpdatedAt());
    }
}
