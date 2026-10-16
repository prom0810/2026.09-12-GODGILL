package com.safewalk.route;

import com.safewalk.global.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 안전 경로 추천 API.
 * 사용자 유형별 가중치를 적용해 경로를 계산한다.
 *  - 고령자: 경사로·계단이 많은 길 회피
 *  - 여성: 야간 CCTV·가로등·큰길 우선
 *  - 아동: 여성 기준 + 차량 통행이 많은 길 회피
 * TODO: 경로 탐색 알고리즘 및 가중치 적용 로직 구현 (천안역 반경 1~2km 범위).
 */
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    @GetMapping("/safe")
    public ApiResponse<String> getSafeRoute() {
        // TODO: 출발지/도착지 좌표, 사용자 유형을 받아 안전 경로 계산
        return ApiResponse.ok("안전 경로 추천 API - 구현 예정");
    }
}
