package com.safewalk.auth;

/**
 * 토큰이 없거나, 위조·만료되었거나, 토큰의 사용자가 더 이상 존재하지 않는 경우.
 * GlobalExceptionHandler가 401로 변환한다.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
