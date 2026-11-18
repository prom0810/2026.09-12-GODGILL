package com.safewalk.report;

import com.safewalk.global.ApiResponse;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 위험구역 조회 API. 토큰 없이 호출할 수 있다 (WebConfig 예외) — 로그인 전에도 앱이 경로 안전도를 계산하기 때문.
 *
 * GET /api/risk-areas → ACTIVE 구역 전체 (천안역 주변 1~2km 범위라 페이지 없이 전부 준다).
 */
@RestController
@RequestMapping("/api/risk-areas")
public class RiskAreaController {

    private final RiskAreaRepository riskAreaRepository;

    public RiskAreaController(RiskAreaRepository riskAreaRepository) {
        this.riskAreaRepository = riskAreaRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<RiskAreaResponse>> list() {
        return ApiResponse.ok(riskAreaRepository.findByStatusOrderByRiskAreaIdAsc(RiskAreaStatus.ACTIVE).stream()
                .map(RiskAreaResponse::from)
                .toList());
    }
}
