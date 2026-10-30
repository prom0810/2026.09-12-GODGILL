package com.safewalk.emergency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryLocationStoreTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 10, 30, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void keepsOnlyLatestPointPerEmergency() {
        InMemoryLocationStore store = new InMemoryLocationStore();

        assertThat(store.update(1L, new LocationPoint(36.81, 127.14, 10.0, T0), Instant.now())).isTrue();
        assertThat(store.update(1L, new LocationPoint(36.82, 127.15, 8.0, T0.plusSeconds(5)), Instant.now())).isTrue();

        assertThat(store.latest(1L)).get().extracting(LocationPoint::lat).isEqualTo(36.82);
        assertThat(store.latest(2L)).isEmpty();
    }

    @Test
    void ignoresPointMeasuredBeforeStoredOne() {
        InMemoryLocationStore store = new InMemoryLocationStore();
        Instant firstReceived = Instant.parse("2026-10-30T12:00:06Z");
        store.update(1L, new LocationPoint(36.82, 127.15, null, T0.plusSeconds(5)), firstReceived);

        // 네트워크 지연으로 더 오래된 위치가 늦게 도착
        boolean accepted = store.update(1L, new LocationPoint(36.81, 127.14, null, T0), Instant.now());

        assertThat(accepted).isFalse();
        assertThat(store.latest(1L)).get().extracting(LocationPoint::lat).isEqualTo(36.82);
        assertThat(store.lastReceivedAt(1L)).contains(firstReceived);
    }

    @Test
    void removeClearsLocation() {
        InMemoryLocationStore store = new InMemoryLocationStore();
        store.update(1L, new LocationPoint(36.81, 127.14, null, T0), Instant.now());

        store.remove(1L);

        assertThat(store.latest(1L)).isEmpty();
        assertThat(store.lastReceivedAt(1L)).isEmpty();
    }
}
