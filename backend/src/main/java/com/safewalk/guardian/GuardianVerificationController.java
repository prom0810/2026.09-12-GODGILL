package com.safewalk.guardian;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자 확인(인증번호) API. 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 * 규칙은 {@link GuardianVerificationService} 참고.
 *
 *  - POST /api/guardians/{guardianId}/verification           보호자 번호로 인증번호 보내기
 *  - POST /api/guardians/{guardianId}/verification/confirm   보호자가 알려 준 번호 입력 → 확인된 보호자 정보 반환
 */
@RestController
@RequestMapping("/api/guardians")
public class GuardianVerificationController {

    private final GuardianVerificationService verificationService;

    public GuardianVerificationController(GuardianVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/{guardianId}/verification")
    public ApiResponse<GuardianVerificationResponses.Sent> sendCode(@LoginUser Long userId,
                                                                    @PathVariable Long guardianId) {
        return ApiResponse.ok(verificationService.sendCode(userId, guardianId));
    }

    /** 요청 예: { "code": "123456" } */
    @PostMapping("/{guardianId}/verification/confirm")
    public ApiResponse<GuardianResponse> confirm(
            @LoginUser Long userId,
            @PathVariable Long guardianId,
            @RequestBody(required = false) GuardianVerificationResponses.ConfirmRequest request
    ) {
        return ApiResponse.ok(verificationService.confirm(userId, guardianId, request));
    }
}
