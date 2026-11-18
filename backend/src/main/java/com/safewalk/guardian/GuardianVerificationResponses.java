package com.safewalk.guardian;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** 보호자 확인(인증번호) 요청·응답 */
public final class GuardianVerificationResponses {

    private GuardianVerificationResponses() {
    }

    /** POST /api/guardians/{guardianId}/verification/confirm 요청. 예: { "code": "123456" } */
    public record ConfirmRequest(String code) {
    }

    /**
     * POST /api/guardians/{guardianId}/verification 응답.
     *
     * - expiresAt: 이 시각까지 입력해야 한다 (발급 + 3분)
     * - resendAvailableAt: 이 시각부터 다시 보낼 수 있다 (발급 + 1분)
     * - devCode: 개발용. .env의 GUARDIAN_VERIFICATION_EXPOSE_CODE=true일 때만 들어간다 (자동 테스트 스크립트용).
     *   운영에서는 반드시 꺼 둘 것. 꺼져 있으면 필드 자체가 응답에 없다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Sent(
            Long guardianId,
            OffsetDateTime expiresAt,
            OffsetDateTime resendAvailableAt,
            String devCode
    ) {
    }
}
