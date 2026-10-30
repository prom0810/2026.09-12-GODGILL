package com.safewalk.emergency;

/**
 * 없는 사건이거나 볼 권한이 없는 사건. 존재 여부를 드러내지 않도록 둘 다 404로 응답한다.
 * GlobalExceptionHandler가 404로 변환한다.
 */
public class EmergencyNotFoundException extends RuntimeException {

    public EmergencyNotFoundException(String message) {
        super(message);
    }
}
