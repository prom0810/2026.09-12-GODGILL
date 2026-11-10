package com.safewalk.route;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로 안내 기록 API. 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 *
 * 경로 계산(최단·안전 A*)은 앱이 한다. 서버는 "안내 시작"을 누른 경로만 저장한다.
 * 규칙은 {@link RouteService}, 요청 형식은 {@link RouteRequests} 참고.
 *
 *  - POST  /api/routes              안내 시작 (경로·지점·구간·시설 저장)
 *  - GET   /api/routes/active       진행 중 경로 (없으면 data: null)
 *  - GET   /api/routes?page=&size=  내 경로 기록 (최신순)
 *  - GET   /api/routes/{routeId}    상세 (지점·구간·시설)
 *  - PATCH /api/routes/{routeId}    도착(COMPLETED) / 안내 취소(CANCELLED)
 */
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public ApiResponse<RouteResponses.Started> start(@LoginUser Long userId,
                                                     @RequestBody RouteRequests.Start request) {
        return ApiResponse.ok(routeService.start(userId, request));
    }

    @GetMapping("/active")
    public ApiResponse<RouteResponses.Summary> getActive(@LoginUser Long userId) {
        return ApiResponse.ok(routeService.getActive(userId));
    }

    @GetMapping
    public ApiResponse<RouteResponses.Page> getRoutes(
            @LoginUser Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(routeService.getRoutes(userId, page, size));
    }

    @GetMapping("/{routeId}")
    public ApiResponse<RouteResponses.Detail> getRoute(@LoginUser Long userId, @PathVariable Long routeId) {
        return ApiResponse.ok(routeService.getRoute(userId, routeId));
    }

    @PatchMapping("/{routeId}")
    public ApiResponse<RouteResponses.Summary> updateStatus(@LoginUser Long userId, @PathVariable Long routeId,
                                                            @RequestBody RouteRequests.UpdateStatus request) {
        return ApiResponse.ok(routeService.updateStatus(userId, routeId, request));
    }
}
