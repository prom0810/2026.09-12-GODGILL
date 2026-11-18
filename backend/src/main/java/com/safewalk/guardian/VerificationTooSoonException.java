package com.safewalk.guardian;

/**
 * 인증번호를 너무 자주 요청함 (1분에 1번). GlobalExceptionHandler가 429 + data { retryAfterSeconds }로 변환한다.
 */
public class VerificationTooSoonException extends RuntimeException {

    private final long retryAfterSeconds;

    public VerificationTooSoonException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
