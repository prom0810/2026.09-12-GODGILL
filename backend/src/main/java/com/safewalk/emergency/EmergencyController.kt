package com.safewalk.emergency

import com.safewalk.global.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * SOS 요청, 실시간 위치 공유, 도착 확인 API.
 * TODO: 실시간 위치 공유(WebSocket 등) 및 보호자 연동 구현.
 */
@RestController
@RequestMapping("/api/emergency")
class EmergencyController {

    @PostMapping("/sos")
    fun triggerSos(): ApiResponse<String> {
        // TODO: SOS 트리거 처리 로직 구현
        return ApiResponse.ok("SOS 요청 API - 구현 예정")
    }

    @PostMapping("/arrival")
    fun confirmArrival(): ApiResponse<String> {
        // TODO: 도착 확인 처리 로직 구현
        return ApiResponse.ok("도착 확인 API - 구현 예정")
    }
}
