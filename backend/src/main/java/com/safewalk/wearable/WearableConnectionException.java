package com.safewalk.wearable;

/**
 * 연결 규칙에 걸림. GlobalExceptionHandler가 409 + data { reason, currentWearable }로 응답한다.
 * currentWearable은 SWITCH_REQUIRED·GUIDANCE_IN_PROGRESS일 때 지금 연결 중인 내 기기, 나머지는 null
 * (다른 계정 정보는 드러내지 않는다).
 */
public class WearableConnectionException extends RuntimeException {

    public enum Reason {
        /** 내 다른 워치가 연결 중 → 사용자가 확인하면 replaceCurrent=true로 다시 요청 */
        SWITCH_REQUIRED("다른 워치가 연결되어 있어요. 기존 워치 연결을 끊고 이 워치를 연결할까요?"),
        /** 경로 안내 중에는 다른 워치로 바꿀 수 없음 */
        GUIDANCE_IN_PROGRESS("경로 안내 중에는 다른 워치로 바꿀 수 없어요."),
        /** 이 워치를 다른 계정에서 연결 중 */
        IN_USE_BY_OTHER_ACCOUNT("다른 계정에서 연결 중인 워치예요.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;
    private final WearableResponse currentWearable;

    public WearableConnectionException(Reason reason, WearableResponse currentWearable) {
        super(reason.message);
        this.reason = reason;
        this.currentWearable = currentWearable;
    }

    public WearableConnectionException(Reason reason) {
        this(reason, null);
    }

    public Reason getReason() {
        return reason;
    }

    public WearableResponse getCurrentWearable() {
        return currentWearable;
    }
}
