package com.safewalk.emergency;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SOS · 보호자 위치 공유 API. 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 * 규칙은 {@link EmergencyService} 참고.
 *
 * 사용자(SOS 당사자)
 *  - POST  /api/emergencies                          SOS 시작 (201)
 *  - POST  /api/emergencies/{id}/locations           위치 전송, 5초마다 (204)
 *  - GET   /api/emergencies/active                   진행 중인 내 SOS (앱 재시작 시 복구)
 * 보호자
 *  - GET   /api/emergencies/shared                   보호 중인 사람들의 진행 중 SOS
 *  - GET   /api/emergencies/{id}/stream              실시간 위치 (SSE)
 *  - GET   /api/emergencies/{id}/locations/latest    최신 위치 1건 (SSE를 못 쓸 때)
 * 공통
 *  - GET   /api/emergencies/{id}                     상세
 *  - PATCH /api/emergencies/{id}                     종료 (보호자는 RESOLVED만)
 */
@RestController
@RequestMapping("/api/emergencies")
public class EmergencyEventController {

    private final EmergencyService emergencyService;

    public EmergencyEventController(EmergencyService emergencyService) {
        this.emergencyService = emergencyService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmergencyResponses.Started>> start(
            @LoginUser Long userId, @RequestBody(required = false) EmergencyRequests.Start request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(emergencyService.start(userId, request)));
    }

    @PostMapping("/{emergencyId}/locations")
    public ResponseEntity<Void> postLocation(@LoginUser Long userId, @PathVariable Long emergencyId,
                                             @RequestBody(required = false) EmergencyRequests.Location request) {
        emergencyService.postLocation(userId, emergencyId, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{emergencyId}")
    public ApiResponse<EmergencyResponses.Resolved> resolve(@LoginUser Long userId, @PathVariable Long emergencyId,
                                                            @RequestBody(required = false) EmergencyRequests.Resolve request) {
        return ApiResponse.ok(emergencyService.resolve(userId, emergencyId, request));
    }

    @GetMapping("/active")
    public ApiResponse<EmergencyResponses.Detail> active(@LoginUser Long userId) {
        return ApiResponse.ok(emergencyService.active(userId));
    }

    @GetMapping("/shared")
    public ApiResponse<List<EmergencyResponses.Shared>> shared(@LoginUser Long userId) {
        return ApiResponse.ok(emergencyService.shared(userId));
    }

    @GetMapping("/{emergencyId}")
    public ApiResponse<EmergencyResponses.Detail> get(@LoginUser Long userId, @PathVariable Long emergencyId) {
        return ApiResponse.ok(emergencyService.get(userId, emergencyId));
    }

    @GetMapping("/{emergencyId}/locations/latest")
    public ApiResponse<LocationPoint> latestLocation(@LoginUser Long userId, @PathVariable Long emergencyId) {
        return ApiResponse.ok(emergencyService.latestLocation(userId, emergencyId));
    }

    /**
     * SSE 스트림. 이벤트: location / location_stale / status (종료 후 연결 닫힘), 20초마다 :ping 주석.
     * 앱은 연결이 끊기면(30분 제한, 네트워크 전환 등) 다시 연결한다. status를 받았으면 다시 연결하지 않는다.
     */
    @GetMapping("/{emergencyId}/stream")
    public SseEmitter stream(@LoginUser Long userId, @PathVariable Long emergencyId) {
        return emergencyService.stream(userId, emergencyId);
    }
}
