package com.safewalk.guardian;

/**
 * 보호자를 찾을 수 없는 경우 (존재하지 않음, 이미 삭제됨, 다른 사용자의 보호자).
 * 다른 사용자의 보호자인지 여부를 드러내지 않기 위해 세 경우 모두 같은 404로 응답한다.
 */
public class GuardianNotFoundException extends RuntimeException {

    public GuardianNotFoundException(String message) {
        super(message);
    }
}
