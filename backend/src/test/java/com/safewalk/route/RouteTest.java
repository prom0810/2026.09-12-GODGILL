package com.safewalk.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RouteTest {

    private final OffsetDateTime started = OffsetDateTime.parse("2026-11-10T09:00:00+09:00");

    @Test
    void completeRecordsCompletedAtOnce() {
        Route route = Route.forTest(1L, RouteStatus.IN_PROGRESS, started);

        route.complete(started.plusMinutes(20));

        assertThat(route.getStatus()).isEqualTo(RouteStatus.COMPLETED);
        assertThat(route.getCompletedAt()).isEqualTo(started.plusMinutes(20));
        assertThatThrownBy(() -> route.complete(started.plusMinutes(30))).isInstanceOf(RouteStateException.class);
        assertThatThrownBy(route::cancel).isInstanceOf(RouteStateException.class);
    }

    @Test
    void completedAtNeverBeforeCreatedAt() {
        Route route = Route.forTest(1L, RouteStatus.IN_PROGRESS, started);

        route.complete(started.minusSeconds(5));

        assertThat(route.getCompletedAt()).isEqualTo(started);
    }

    @Test
    void cancelLeavesCompletedAtEmpty() {
        Route route = Route.forTest(1L, RouteStatus.IN_PROGRESS, started);

        route.cancel();

        assertThat(route.getStatus()).isEqualTo(RouteStatus.CANCELLED);
        assertThat(route.getCompletedAt()).isNull();
    }
}
