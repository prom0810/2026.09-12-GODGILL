package com.safewalk.auth;

/** 로그인 시 이메일 또는 비밀번호가 일치하지 않는 경우. GlobalExceptionHandler가 401로 변환한다. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
