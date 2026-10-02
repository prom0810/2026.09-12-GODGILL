package com.safewalk.auth;

/** 이미 가입된 전화번호로 회원가입을 시도한 경우. GlobalExceptionHandler가 409로 변환한다. */
public class DuplicatePhoneException extends RuntimeException {

    public DuplicatePhoneException(String message) {
        super(message);
    }
}
