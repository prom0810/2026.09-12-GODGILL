package com.safewalk.emergency;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 서버 메모리 구현. 서버 1대일 때만 맞다 (MSA 전환 시 Redis 구현으로 교체). */
@Component
public class InMemoryLocationStore implements LocationStore {

    private record Entry(LocationPoint point, Instant receivedAt) {
    }

    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean update(Long emergencyId, LocationPoint point, Instant receivedAt) {
        boolean[] accepted = {false};
        entries.compute(emergencyId, (id, old) -> {
            if (old != null && point.recordedAt().isBefore(old.point().recordedAt())) {
                return old;
            }
            accepted[0] = true;
            return new Entry(point, receivedAt);
        });
        return accepted[0];
    }

    @Override
    public Optional<LocationPoint> latest(Long emergencyId) {
        return Optional.ofNullable(entries.get(emergencyId)).map(Entry::point);
    }

    @Override
    public Optional<Instant> lastReceivedAt(Long emergencyId) {
        return Optional.ofNullable(entries.get(emergencyId)).map(Entry::receivedAt);
    }

    @Override
    public void remove(Long emergencyId) {
        entries.remove(emergencyId);
    }
}
