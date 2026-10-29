package com.safewalk.guardian;

/**
 * 보호자 연결 상태 때문에 요청을 처리할 수 없는 경우. GlobalExceptionHandler가 409로 변환한다.
 * 예: 이미 연결된 보호자에게 다시 초대, 초대 중이 아닌 보호자 수락
 */
public class GuardianStateException extends RuntimeException {

    public GuardianStateException(String message) {
        super(message);
    }
}
