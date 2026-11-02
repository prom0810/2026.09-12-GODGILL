package com.safewalk.emergency;

import com.safewalk.global.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 도착 확인 API (경로 안내 기능과 함께 구현 예정).
 * SOS·위치 공유는 /api/emergencies (EmergencyEventController, 구현 중)로 옮겼다.
 */
@RestController
@RequestMapping("/api/emergency")
public class EmergencyController {

    @PostMapping("/arrival")
    public ApiResponse<String> confirmArrival() {
        // TODO: 도착 확인 처리 로직 구현
        return ApiResponse.ok("도착 확인 API - 구현 예정");
    }
}
