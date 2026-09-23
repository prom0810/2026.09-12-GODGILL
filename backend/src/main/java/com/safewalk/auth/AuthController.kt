package com.safewalk.auth

import com.safewalk.global.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회원가입/로그인 API.
 *
 * 현 단계 범위: 이메일·비밀번호·사용자 유형(ADULT/MINOR)만 받아 users 테이블에 저장한다.
 * 이메일 형식 검증, 비밀번호 길이 제한 같은 세부 검증은 하지 않는다 — 빈 값 여부와
 * 이메일 중복(회원가입) / 이메일·비밀번호 일치(로그인) 여부만 [AuthService]에서 확인한다.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ApiResponse<AuthResponse> {
        return ApiResponse.ok(authService.signup(request))
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ApiResponse<AuthResponse> {
        return ApiResponse.ok(authService.login(request))
    }
}
