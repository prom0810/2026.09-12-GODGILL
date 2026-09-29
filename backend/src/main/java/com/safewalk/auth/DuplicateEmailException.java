package com.safewalk.auth;

/** 이미 가입된 이메일로 회원가입을 시도한 경우. GlobalExceptionHandler가 409로 변환한다. */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
