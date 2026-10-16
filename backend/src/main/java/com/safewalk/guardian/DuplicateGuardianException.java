package com.safewalk.guardian;

/** 같은 사용자에게 같은 전화번호의 보호자를 다시 등록하려는 경우. GlobalExceptionHandler가 409로 변환한다. */
public class DuplicateGuardianException extends RuntimeException {

    public DuplicateGuardianException(String message) {
        super(message);
    }
}
