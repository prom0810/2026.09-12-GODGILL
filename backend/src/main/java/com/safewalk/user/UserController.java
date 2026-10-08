package com.safewalk.user;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 정보 API. 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 * 보호자 관리는 {@link com.safewalk.guardian.GuardianController}(/api/guardians)에서 담당한다.
 * TODO: 사용자 유형별 안전 기준 설정/조회 로직 구현.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 토큰의 주인(현재 로그인한 사용자) 정보.
     * 앱 재실행 시 자동 로그인 확인(성공 → 메인, 401 → 로그인 화면)에도 사용한다.
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@LoginUser Long userId) {
        return ApiResponse.ok(userService.getMyInfo(userId));
    }
}
