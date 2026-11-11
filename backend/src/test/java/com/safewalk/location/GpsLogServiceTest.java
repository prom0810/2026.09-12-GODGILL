package com.safewalk.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.safewalk.emergency.EmergencyEventRepository;
import com.safewalk.emergency.EmergencySseHub;
import com.safewalk.emergency.LocationStore;
import com.safewalk.guardian.Guardian;
import com.safewalk.guardian.GuardianRepository;
import com.safewalk.guardian.RegistrationStatus;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GpsLogServiceTest {
    @Mock RedisGpsStore store;
    @Mock UserService users;
    @Mock GuardianRepository guardians;
    @Mock EmergencyEventRepository emergencies;
    @Mock LocationStore emergencyLocations;
    @Mock EmergencySseHub emergencySse;
    @Mock LocationSseHub locationSse;
    private GpsLogService service;

    @BeforeEach
    void setup() {
        service = new GpsLogService(store, users, guardians, emergencies,
                emergencyLocations, emergencySse, locationSse);
    }

    @Test
    void guardianNeedsConnectedAndLocationPermission() {
        when(guardians.findFirstByUser_UserIdAndGuardianUser_UserIdAndRegistrationStatusAndDeletedAtIsNull(
                10L, 20L, RegistrationStatus.CONNECTED)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.latest(20L, 10L)).isInstanceOf(LocationNotFoundException.class);
        verify(store, never()).latest(any());
    }

    @Test
    void permittedGuardianCanReadLatestFix() {
        Guardian guardian = org.mockito.Mockito.mock(Guardian.class);
        User ward = org.mockito.Mockito.mock(User.class);
        when(guardian.isCanViewLocation()).thenReturn(true);
        when(guardian.getUser()).thenReturn(ward);
        when(guardians.findFirstByUser_UserIdAndGuardianUser_UserIdAndRegistrationStatusAndDeletedAtIsNull(
                10L, 20L, RegistrationStatus.CONNECTED)).thenReturn(Optional.of(guardian));
        GpsPoint point = new GpsPoint(10L, "session", 36.8, 127.1, Instant.now(), 5.0);
        when(store.latest(10L)).thenReturn(Optional.of(point));
        assertThat(service.latest(20L, 10L)).isEqualTo(point);
    }

    @Test
    void duplicateFixDoesNotPublishToSse() {
        Instant time = Instant.now();
        GpsRequest request = new GpsRequest(36.8, 127.1, time, 5.0);
        when(store.append(new GpsPoint(10L, "session", 36.8, 127.1, time, 5.0))).thenReturn(0);
        assertThat(service.append(10L, "session", request)).isFalse();
        verify(locationSse, never()).publish(any());
        verify(emergencies, never()).findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(any(), any());
    }

    @Test
    void rejectsInvalidLocationBeforeWriting() {
        assertThatThrownBy(() -> service.append(10L, "session",
                new GpsRequest(91.0, 127.1, Instant.now(), 5.0)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(store, never()).append(any());
    }
}
