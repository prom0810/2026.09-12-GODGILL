package com.safewalk.guardian;

/**
 * 인증번호 확인 실패. GlobalExceptionHandler가 400 + data { reason, remainingAttempts }로 변환한다.
 *
 * reason
 *  - NOT_REQUESTED : 발급된 번호가 없음 → 인증번호를 다시 보내야 함
 *  - EXPIRED       : 3분이 지남 → 다시 보내야 함
 *  - MISMATCH      : 번호가 틀림 → remainingAttempts번 더 시도 가능
 *  - LOCKED        : 5번 틀려서 무효 → 다시 보내야 함
 */
public class GuardianVerificationException extends RuntimeException {

    private final GuardianVerificationCodeStore.Result reason;
    private final int remainingAttempts;

    public GuardianVerificationException(String message, GuardianVerificationCodeStore.Result reason,
                                         int remainingAttempts) {
        super(message);
        this.reason = reason;
        this.remainingAttempts = remainingAttempts;
    }

    public GuardianVerificationCodeStore.Result getReason() {
        return reason;
    }

    public int getRemainingAttempts() {
        return remainingAttempts;
    }
}
