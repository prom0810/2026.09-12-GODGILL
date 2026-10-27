package com.safewalk.notification;

import java.util.List;

/**
 * 여러 기기에 보낸 푸시의 결과.
 *
 * - successTokens: 발송 성공
 * - invalidTokens: 앱 삭제·토큰 만료 등으로 더 이상 쓸 수 없는 토큰 → user_device를 비활성화한다
 * - failedTokens: 그 밖의 이유(네트워크, 인증 등)로 실패. 토큰 자체는 유효할 수 있으므로 유지한다
 * - errorCodes: 실패 원인 코드 목록 (예: UNREGISTERED, INVALID_ARGUMENT, THIRD_PARTY_AUTH_ERROR). 중복 제거
 */
public record PushResult(
        String mode,
        List<String> successTokens,
        List<String> invalidTokens,
        List<String> failedTokens,
        List<String> errorCodes) {

    public PushResult {
        successTokens = List.copyOf(successTokens);
        invalidTokens = List.copyOf(invalidTokens);
        failedTokens = List.copyOf(failedTokens);
        errorCodes = List.copyOf(errorCodes);
    }

    public static PushResult empty(String mode) {
        return new PushResult(mode, List.of(), List.of(), List.of(), List.of());
    }
}
