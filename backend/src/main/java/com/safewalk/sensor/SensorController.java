package com.safewalk.sensor;

import com.safewalk.global.LoginUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 워치 센서 수집 API (심박 이상 SOS 동안만).
 *
 *  POST /api/sensors/telemetry?emergencyId={id}   Content-Type: application/octet-stream
 *       본문 = 워치 TelemetryStreamer 배치(gzip) 그대로. 서버는 심박만 읽는다. 204
 *       400 형식 오류, 409 {reason: EMERGENCY_NOT_ACTIVE | WEARABLE_NOT_CONNECTED} → 앱은 스트리밍 중단
 */
@RestController
@RequestMapping("/api/sensors")
public class SensorController {

    private final SensorStreamService service;

    public SensorController(SensorStreamService service) {
        this.service = service;
    }

    @PostMapping(value = "/telemetry", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> telemetry(@LoginUser Long userId, @RequestParam Long emergencyId,
                                          @RequestBody byte[] body) {
        service.ingest(userId, emergencyId, body);
        return ResponseEntity.noContent().build();
    }
}
