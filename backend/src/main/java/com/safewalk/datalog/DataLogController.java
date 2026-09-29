package com.safewalk.datalog;

import com.safewalk.global.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웨어러블 기기(심박수, 걸음, 낙상, 생체신호 등) 데이터 수신 및 이상 탐지 API.
 * TODO: 낙상/생체신호 이상 판정 로직 구현.
 */
@RestController
@RequestMapping("/api/datalog")
public class DataLogController {

    @PostMapping("/vitals")
    public ApiResponse<String> receiveVitals() {
        // TODO: 웨어러블 생체신호 데이터 수신 및 저장 로직 구현
        return ApiResponse.ok("생체신호 데이터 수신 API - 구현 예정");
    }

    @PostMapping("/falldetect")
    public ApiResponse<String> receiveFallDetection() {
        // TODO: 낙상 감지 이벤트 수신 및 알림 트리거 로직 구현
        return ApiResponse.ok("낙상 감지 이벤트 수신 API - 구현 예정");
    }
}
