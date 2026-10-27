package com.safewalk.roadimage;

import com.safewalk.global.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 도로 이미지 AI 분석 결과 조회 API. */
@RestController
@RequestMapping("/api/road-images")
public class RoadImageAnalysisController {

    private final RoadImageAnalysisService service;

    public RoadImageAnalysisController(RoadImageAnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<RoadImageAnalysisResponse>> findAll() {
        return ApiResponse.ok(service.findAll());
    }

    @GetMapping("/nearby")
    public ApiResponse<List<NearbyRoadImageAnalysisResponse>> findNearby(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "100") double radiusMeters,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(service.findNearby(latitude, longitude, radiusMeters, limit));
    }
}
