package com.safewalk.location;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/locations")
public class GpsLogController {
    private final GpsLogService service;

    public GpsLogController(GpsLogService service) { this.service = service; }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<Map<String, String>>> start(@LoginUser Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("sessionId", service.start(userId))));
    }

    @PostMapping("/sessions/{sessionId}/logs")
    public ResponseEntity<Void> append(@LoginUser Long userId, @PathVariable String sessionId,
                                       @RequestBody GpsRequest request) {
        service.append(userId, sessionId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> close(@LoginUser Long userId, @PathVariable String sessionId) {
        service.close(userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/latest")
    public ApiResponse<GpsPoint> mine(@LoginUser Long userId) {
        return ApiResponse.ok(service.latest(userId, userId));
    }

    @GetMapping("/users/{userId}/latest")
    public ApiResponse<GpsPoint> latest(@LoginUser Long viewerId, @PathVariable Long userId) {
        return ApiResponse.ok(service.latest(viewerId, userId));
    }

    @GetMapping("/users/{userId}/sessions/{sessionId}/logs")
    public ApiResponse<List<GpsPoint>> range(@LoginUser Long viewerId, @PathVariable Long userId,
            @PathVariable String sessionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(service.range(viewerId, userId, sessionId, from, to));
    }

    @GetMapping(value = "/users/{userId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@LoginUser Long viewerId, @PathVariable Long userId) {
        return service.stream(viewerId, userId);
    }
}
