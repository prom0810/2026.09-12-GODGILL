package com.safewalk.wearable;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** 등록된 웨어러블 기기. isActive = 지금 앱과 연결 중, lastConnectedAt = 마지막 연결 상태 변경 시각 */
public record WearableResponse(
        Long wearableId,
        String deviceType,
        String deviceIdentifier,
        String deviceName,
        @JsonProperty("isActive") boolean isActive,
        OffsetDateTime connectedAt,
        OffsetDateTime lastConnectedAt
) {

    public static WearableResponse from(WearableDevice d) {
        return new WearableResponse(d.getWearableId(), d.getDeviceType().name(), d.getDeviceIdentifier(),
                d.getDeviceName(), d.isActive(), d.getConnectedAt(), d.getLastConnectedAt());
    }

    /**
     * PATCH /api/wearables/{wearableId}/connection 응답.
     * disconnectedWearableId: 전환(replaceCurrent=true)하면서 해제한 내 기존 기기 ID, 없으면 null
     */
    public record Connection(WearableResponse wearable, Long disconnectedWearableId) {
    }
}
