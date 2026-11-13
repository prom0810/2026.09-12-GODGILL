package com.safewalk.wearable;

import java.util.Locale;

/**
 * wearable_device.device_type (DB ck_wearable_device_type).
 *
 * - WEAR_OS: Galaxy Watch 등. 워치 → 폰(Wearable Data Layer) → 서버 순서로 전달된다
 * - CUSTOM : 직접 만든 기기(M5StickC, ESP32). 기기가 도착하면 연결 방식을 정한다
 */
public enum WearableType {
    WEAR_OS,
    CUSTOM;

    public static WearableType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("deviceType(WEAR_OS 또는 CUSTOM)을 입력해 주세요.");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("deviceType은 WEAR_OS 또는 CUSTOM이어야 합니다: " + raw);
        }
    }
}
