package com.safewalk.sensor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safewalk.emergency.EmergencyEvent;
import com.safewalk.emergency.EmergencyEventRepository;
import com.safewalk.emergency.EmergencyType;
import com.safewalk.wearable.WearableDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 심박 이상 SOS 동안만 워치 심박을 받는다 (경로 안내 중 평소에는 받지 않음, 2026-11-13 결정).
 *
 * 흐름
 *  1. 워치가 심박 이상 감지 → 폰 팝업 무응답 → SOS(type=ABNORMAL_HEART_RATE, bpm=감지 당시 심박) 생성
 *     → {@link #beginHeartRate}로 발생 당시 심박을 Redis에 기록
 *  2. 폰이 워치 스트리밍을 켜고, 5초 배치를 POST /api/sensors/telemetry?emergencyId= 로 그대로 전달
 *     → 서버는 배치에서 심박만 꺼내 최신 값을 Redis에 갱신 (요약 계산 없음)
 *  3. SOS 종료 → {@link BpmLogWriter}가 발생 당시·종료 시 심박을 bpm_log에 저장하고 Redis 값 삭제
 *     → 그 뒤 들어오는 배치는 409 → 폰이 스트리밍을 끈다
 * 낙상·직접 누른 SOS는 센서를 받지 않는다 (낙상은 "발생했다"는 사건 자체가 기록).
 */
@Service
public class SensorStreamService {

    private final RedisSensorStore store;
    private final SensorProperties props;
    private final EmergencyEventRepository eventRepository;
    private final WearableDeviceRepository wearableRepository;
    private final ObjectMapper mapper;

    public SensorStreamService(RedisSensorStore store, SensorProperties props, EmergencyEventRepository eventRepository,
                               WearableDeviceRepository wearableRepository, ObjectMapper mapper) {
        this.store = store;
        this.props = props;
        this.eventRepository = eventRepository;
        this.wearableRepository = wearableRepository;
        this.mapper = mapper;
    }

    /** 배치 1개 수신. 심박이 없는 배치도 정상(204) */
    @Transactional(readOnly = true)
    public void ingest(Long userId, Long emergencyId, byte[] body) {
        if (emergencyId == null || emergencyId <= 0) {
            throw new IllegalArgumentException("emergencyId를 입력해 주세요.");
        }
        TelemetryParser.Parsed parsed = TelemetryParser.parse(body, props.maxBatchBytes(), mapper);
        EmergencyEvent event = eventRepository.findById(emergencyId)
                .filter(e -> e.isOwnedBy(userId) && e.isOpen() && e.getType() == EmergencyType.ABNORMAL_HEART_RATE)
                .orElseThrow(() -> new SensorStreamException(SensorStreamException.Reason.EMERGENCY_NOT_ACTIVE));
        boolean sameWatch = event.getWearableId() != null && wearableRepository.findById(event.getWearableId())
                .filter(w -> w.getDeletedAt() == null && w.getDeviceIdentifier().equals(parsed.deviceId()))
                .isPresent();
        if (!sameWatch) {
            throw new SensorStreamException(SensorStreamException.Reason.WEARABLE_NOT_CONNECTED);
        }
        if (parsed.lastBpm() != null) {
            store.record(emergencyId, parsed.firstBpm(), parsed.firstBpmAtMs(), parsed.lastBpm(),
                    parsed.lastBpmAtMs(), props.bpmTtlSeconds());
        }
    }

    /** 심박 이상 SOS 시작(커밋 뒤). bpm = 워치가 감지할 때 잰 심박. 없으면 SOS 후 첫 배치 값이 발생 당시 심박이 된다 */
    public void beginHeartRate(long emergencyId, Integer bpm, long occurredAtMs) {
        if (bpm != null) {
            store.begin(emergencyId, bpm, occurredAtMs, props.bpmTtlSeconds());
        }
    }
}
