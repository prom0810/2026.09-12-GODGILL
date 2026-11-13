package com.safewalk.wearable;

/** 웨어러블 API 요청 본문 */
public final class WearableRequests {

    private WearableRequests() {
    }

    /**
     * PUT /api/wearables — 기기 등록(없으면 생성, 있으면 이름만 갱신). 등록만으로는 연결되지 않는다.
     * { "deviceType": "WEAR_OS", "deviceIdentifier": "워치 ANDROID_ID", "deviceName": "SM-R910" }
     */
    public record Register(String deviceType, String deviceIdentifier, String deviceName) {
    }

    /**
     * PATCH /api/wearables/{wearableId}/connection — 연결 또는 해제.
     * { "connected": true, "replaceCurrent": false }
     *  - replaceCurrent: 내 다른 워치가 연결 중일 때(409 SWITCH_REQUIRED) 사용자가 확인했으면 true.
     *    기존 워치를 해제하고 이 워치를 연결한다. 경로 안내 중에는 true여도 바꾸지 않는다
     */
    public record Connection(Boolean connected, Boolean replaceCurrent) {
    }
}
