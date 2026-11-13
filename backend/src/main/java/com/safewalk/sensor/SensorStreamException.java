package com.safewalk.sensor;

/**
 * 센서 배치를 받을 수 없는 상태. GlobalExceptionHandler가 409 + data { reason }으로 응답한다.
 * 앱은 이 응답을 받으면 워치 스트리밍을 멈춘다.
 */
public class SensorStreamException extends RuntimeException {

    public enum Reason {
        /** emergencyId가 내 진행 중인 심박 이상 SOS가 아님 (종료됐거나 다른 유형·다른 사람 사건) */
        EMERGENCY_NOT_ACTIVE("진행 중인 심박 이상 SOS가 아닙니다. 센서 전송을 멈춰 주세요."),
        /** 배치를 보낸 워치가 이 SOS를 감지한 워치가 아님 */
        WEARABLE_NOT_CONNECTED("이 SOS를 감지한 워치의 데이터가 아닙니다.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public SensorStreamException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
