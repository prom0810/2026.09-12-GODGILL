package com.safewalk.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmergencyEventTest {

    @Test
    void sosStartsOpenWithoutResolver() {
        EmergencyEvent event = EmergencyEvent.sos(null, EmergencySource.MOBILE, 36.81, 127.14);

        assertThat(event.getType()).isEqualTo(EmergencyType.SOS);
        assertThat(event.getStatus()).isEqualTo(EmergencyStatus.OCCURRED);
        assertThat(event.isOpen()).isTrue();
        assertThat(event.getResolvedAt()).isNull();
    }

    @Test
    void sosRequiresBothCoordinatesOrNeither() {
        assertThatThrownBy(() -> EmergencyEvent.sos(null, EmergencySource.MOBILE, 36.81, null))
                .isInstanceOf(IllegalArgumentException.class);

        EmergencyEvent noGps = EmergencyEvent.sos(null, EmergencySource.WEARABLE, null, null);
        assertThat(noGps.getLat()).isNull();
    }

    @Test
    void resolveClosesOnceAndRejectsSecondClose() {
        EmergencyEvent event = EmergencyEvent.sos(null, EmergencySource.MOBILE, null, null);

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
        EmergencyEvent event = EmergencyEvent.sos(null, EmergencySource.MOBILE, null, null);

        assertThatThrownBy(() -> event.resolve(EmergencyStatus.OCCURRED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event.isOpen()).isTrue();
    }

    @Test
    void autoCloseCancelsWithoutResolver() {
        EmergencyEvent event = EmergencyEvent.sos(null, EmergencySource.MOBILE, null, null);

        event.autoClose();

        assertThat(event.getStatus()).isEqualTo(EmergencyStatus.CANCELLED);
        assertThat(event.getResolvedBy()).isNull();
    }
}
