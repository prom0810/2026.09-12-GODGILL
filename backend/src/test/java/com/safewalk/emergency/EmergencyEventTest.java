package com.safewalk.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmergencyEventTest {

    @Test
    void sosStartsOpenWithoutResolver() {
        EmergencyEvent event = EmergencyEvent.sos(null, EmergencySource.MOBILE);

        assertThat(event.getType()).isEqualTo(EmergencyType.SOS);
        assertThat(event.getStatus()).isEqualTo(EmergencyStatus.OCCURRED);
        assertThat(event.isOpen()).isTrue();
        assertThat(event.getResolvedAt()).isNull();
    }

    @Test
    void sosRejectsSystemSource() {
        assertThatThrownBy(() -> EmergencyEvent.sos(null, EmergencySource.SYSTEM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void autoDetectedNeedsWatchRouteSegment() {
        EmergencyEvent fall = EmergencyEvent.autoDetected(null, EmergencyType.FALL, 1L, 2L, 3L);
        assertThat(fall.getSource()).isEqualTo(EmergencySource.WEARABLE);
        assertThat(fall.getSegmentId()).isEqualTo(3L);
        assertThatThrownBy(() -> EmergencyEvent.autoDetected(null, EmergencyType.ABNORMAL_HEART_RATE, 1L, 2L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmergencyEvent.autoDetected(null, EmergencyType.SOS, 1L, 2L, 3L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveClosesOnceAndRejectsSecondClose() {
        EmergencyEvent event = EmergencyEvent.sos(null, EmergencySource.MOBILE);

        event.resolve(EmergencyStatus.FALSE_ALARM, null);

        assertThat(event.getStatus()).isEqualTo(EmergencyStatus.FALSE_ALARM);
        assertThat(event.getResolvedAt()).isNotNull();
        assertThat(event.getResolvedAt()).isAfterOrEqualTo(event.getOccurredAt());
        assertThatThrownBy(() -> event.resolve(EmergencyStatus.RESOLVED, null))
                .isInstanceOf(EmergencyStateException.class);
        assertThatThrownBy(event::autoClose).isInstanceOf(EmergencyStateException.class);
    }

    @Test
    void resolveRejectsOpenStatus() {
        EmergencyEvent event = EmergencyEvent.sos(null, EmergencySource.MOBILE);

        assertThatThrownBy(() -> event.resolve(EmergencyStatus.OCCURRED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event.isOpen()).isTrue();
    }

    @Test
    void autoCloseCancelsWithoutResolver() {
        EmergencyEvent event = EmergencyEvent.sos(null, EmergencySource.MOBILE);

        event.autoClose();

        assertThat(event.getStatus()).isEqualTo(EmergencyStatus.CANCELLED);
        assertThat(event.getResolvedBy()).isNull();
    }
}
