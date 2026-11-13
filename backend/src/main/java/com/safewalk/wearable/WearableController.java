package com.safewalk.wearable;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웨어러블 기기 API. 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 * 워치는 서버와 직접 통신하지 않고, 폰 앱이 워치 메시지를 받아 대신 호출한다.
 *
 *  - PUT    /api/wearables                         기기 등록·갱신 (연결은 바뀌지 않음. 규칙: {@link WearableService})
 *  - GET    /api/wearables                         내 기기 목록 (isActive = 연결 중)
 *  - PATCH  /api/wearables/{wearableId}/connection 연결·해제 { connected, replaceCurrent? }
 *      → 규칙에 걸리면 409 + data { reason, currentWearable }
 *  - DELETE /api/wearables/{wearableId}            기기 삭제 (연결도 해제)
 */
@RestController
@RequestMapping("/api/wearables")
public class WearableController {

    private final WearableService wearableService;

    public WearableController(WearableService wearableService) {
        this.wearableService = wearableService;
    }

    @PutMapping
    public ApiResponse<WearableResponse> register(@LoginUser Long userId,
                                                  @RequestBody(required = false) WearableRequests.Register request) {
        return ApiResponse.ok(wearableService.register(userId, request));
    }

    @GetMapping
    public ApiResponse<List<WearableResponse>> list(@LoginUser Long userId) {
        return ApiResponse.ok(wearableService.list(userId));
    }

    @PatchMapping("/{wearableId}/connection")
    public ApiResponse<WearableResponse.Connection> updateConnection(
            @LoginUser Long userId, @PathVariable Long wearableId,
            @RequestBody(required = false) WearableRequests.Connection request) {
        return ApiResponse.ok(wearableService.updateConnection(userId, wearableId, request));
    }

    @DeleteMapping("/{wearableId}")
    public ApiResponse<Void> delete(@LoginUser Long userId, @PathVariable Long wearableId) {
        wearableService.delete(userId, wearableId);
        return ApiResponse.ok();
    }
}
