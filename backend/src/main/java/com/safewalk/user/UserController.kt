package com.safewalk.user

import com.safewalk.global.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 유형(학생·여성·고령자·교통약자), 보호자 연락처 관리 API.
 * TODO: 사용자 유형별 안전 기준 설정/조회 로직 구현.
 */
@RestController
@RequestMapping("/api/users")
class UserController {

    @GetMapping("/me")
    fun getMyInfo(): ApiResponse<String> {
        // TODO: 사용자 정보 조회 로직 구현
        return ApiResponse.ok("내 정보 조회 API - 구현 예정")
    }
}
