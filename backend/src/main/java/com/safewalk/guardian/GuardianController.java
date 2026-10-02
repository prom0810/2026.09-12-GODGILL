package com.safewalk.guardian;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자 추가·조회·삭제 API. 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 * 어느 사용자의 보호자인지는 요청 바디가 아니라 토큰에서 결정한다(@LoginUser).
 */
@RestController
@RequestMapping("/api/guardians")
public class GuardianController {

    private final GuardianService guardianService;

    public GuardianController(GuardianService guardianService) {
        this.guardianService = guardianService;
    }

    @PostMapping
    public ApiResponse<GuardianResponse> addGuardian(@LoginUser Long userId, @RequestBody GuardianRequest request) {
        return ApiResponse.ok(guardianService.addGuardian(userId, request));
    }

    @GetMapping
    public ApiResponse<List<GuardianResponse>> getGuardians(@LoginUser Long userId) {
        return ApiResponse.ok(guardianService.getGuardians(userId));
    }

    /** 성공 시 { "success": true, "data": null, "message": null } */
    @DeleteMapping("/{guardianId}")
    public ApiResponse<Void> deleteGuardian(@LoginUser Long userId, @PathVariable Long guardianId) {
        guardianService.deleteGuardian(userId, guardianId);
        return ApiResponse.ok();
    }
}
