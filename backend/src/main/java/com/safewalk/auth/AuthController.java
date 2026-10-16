package com.safewalk.auth;

import com.safewalk.global.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입/로그인 API.
 *
 * 회원가입: 이메일·비밀번호·이름·전화번호·사용자 유형(ADULT/MINOR)과 보호자 목록을 한 번에 받아 저장한다.
 * 로그인: 이메일·비밀번호가 일치하면 JWT를 발급한다.
 * 검증 규칙은 {@link AuthService} 참고.
 *
 * /api/auth/**는 토큰을 발급받는 곳이므로 JWT 검사 대상에서 제외된다({@link com.safewalk.global.WebConfig}).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ApiResponse<AuthResponse> signup(@RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
