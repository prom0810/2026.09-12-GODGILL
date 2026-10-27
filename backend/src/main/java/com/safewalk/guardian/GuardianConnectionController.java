package com.safewalk.guardian;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자 계정 연결 API (초대 → 수락 → 해제). 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 * 규칙은 {@link GuardianConnectionService} 참고.
 *
 * 사용자(보호받는 사람) 쪽
 *  - POST /api/guardians/{guardianId}/invite          초대 보내기
 *  - POST /api/guardians/{guardianId}/revoke          초대 취소 / 연결 해제
 *
 * 보호자 계정 쪽
 *  - GET  /api/guardians/invitations                  받은 초대 목록
 *  - POST /api/guardians/invitations/{guardianId}/accept   수락
 *  - POST /api/guardians/invitations/{guardianId}/reject   거절
 *  - GET  /api/guardians/wards                        내가 보호 중인 사용자 목록
 *  - POST /api/guardians/wards/{guardianId}/leave     연결 끊기
 */
@RestController
@RequestMapping("/api/guardians")
public class GuardianConnectionController {

    private final GuardianConnectionService connectionService;

    public GuardianConnectionController(GuardianConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/{guardianId}/invite")
    public ApiResponse<GuardianResponse> invite(@LoginUser Long userId, @PathVariable Long guardianId) {
        return ApiResponse.ok(connectionService.invite(userId, guardianId));
    }

    @PostMapping("/{guardianId}/revoke")
    public ApiResponse<GuardianResponse> revoke(@LoginUser Long userId, @PathVariable Long guardianId) {
        return ApiResponse.ok(connectionService.revoke(userId, guardianId));
    }

    @GetMapping("/invitations")
    public ApiResponse<List<GuardianInvitationResponse>> getInvitations(@LoginUser Long userId) {
        return ApiResponse.ok(connectionService.getInvitations(userId));
    }

    @PostMapping("/invitations/{guardianId}/accept")
    public ApiResponse<WardResponse> accept(@LoginUser Long userId, @PathVariable Long guardianId) {
        return ApiResponse.ok(connectionService.accept(userId, guardianId));
    }

    /** 성공 시 { "success": true, "data": null, "message": null } */
    @PostMapping("/invitations/{guardianId}/reject")
    public ApiResponse<Void> reject(@LoginUser Long userId, @PathVariable Long guardianId) {
        connectionService.reject(userId, guardianId);
        return ApiResponse.ok();
    }

    @GetMapping("/wards")
    public ApiResponse<List<WardResponse>> getWards(@LoginUser Long userId) {
        return ApiResponse.ok(connectionService.getWards(userId));
    }

    /** 성공 시 { "success": true, "data": null, "message": null } */
    @PostMapping("/wards/{guardianId}/leave")
    public ApiResponse<Void> leave(@LoginUser Long userId, @PathVariable Long guardianId) {
        connectionService.leave(userId, guardianId);
        return ApiResponse.ok();
    }
}
