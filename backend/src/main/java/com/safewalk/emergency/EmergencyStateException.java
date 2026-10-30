package com.safewalk.emergency;

/** 사건 상태 때문에 처리할 수 없는 요청 (예: 이미 종료된 SOS 종료). GlobalExceptionHandler가 409로 변환한다. */
public class EmergencyStateException extends RuntimeException {

    public EmergencyStateException(String message) {
        super(message);
    }
}
