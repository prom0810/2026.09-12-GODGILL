package com.safewalk.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.safewalk.emergency.EmergencyEventRepository;
import com.safewalk.emergency.EmergencyEvent;
import com.safewalk.emergency.LocationPoint;
import com.safewalk.emergency.EmergencySseHub;
import com.safewalk.emergency.LocationStore;
import com.safewalk.guardian.Guardian;
import com.safewalk.guardian.GuardianRepository;
import com.safewalk.guardian.RegistrationStatus;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class GpsLogServiceTest {
    @Mock RedisGpsStore store;
    @Mock SosLocationLogRepository sosLogs;
    @Mock UserService users;
    @Mock GuardianRepository guardians;
    @Mock EmergencyEventRepository emergencies;
    @Mock LocationStore emergencyLocations;
    @Mock EmergencySseHub emergencySse;
    @Mock LocationSseHub locationSse;
    private GpsLogService service;

    @BeforeEach
    void setup() {
        service = new GpsLogService(store, sosLogs, users, guardians, emergencies,
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
        verify(sosLogs, never()).save(any(), any());
    }

    @Test
    void rejectsInvalidLocationBeforeWriting() {
        assertThatThrownBy(() -> service.append(10L, "session",
                new GpsRequest(91.0, 127.1, Instant.now(), 5.0)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(store, never()).append(any());
    }

    @Test
    void activeSosWritesDirectlyToPostgres() {
        Instant time = Instant.now();
        GpsPoint point = new GpsPoint(10L, "session", 36.8, 127.1, time, 5.0);
        EmergencyEvent event = org.mockito.Mockito.mock(EmergencyEvent.class);
        when(event.getEmergencyId()).thenReturn(7L);
        when(emergencies.findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(
                10L, EmergencyEventRepository.OPEN_STATUSES)).thenReturn(Optional.of(event));
        when(store.appendLive(point)).thenReturn(2);

        assertThat(service.append(10L, "session", new GpsRequest(36.8, 127.1, time, 5.0))).isTrue();
        verify(sosLogs).save(7L, point);
        verify(store, never()).append(point);
        verify(locationSse).publish(point);
    }

    @Test
    void activeSosStillWritesToPostgresWhenRedisIsOffline() {
        Instant time = Instant.now();
        GpsPoint point = new GpsPoint(10L, "session", 36.8, 127.1, time, 5.0);
        EmergencyEvent event = org.mockito.Mockito.mock(EmergencyEvent.class);
        when(event.getEmergencyId()).thenReturn(7L);
        when(emergencies.findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(
                10L, EmergencyEventRepository.OPEN_STATUSES)).thenReturn(Optional.of(event));
        when(store.appendLive(point)).thenThrow(new RedisConnectionFailureException("offline"));

        assertThat(service.append(10L, "session", new GpsRequest(36.8, 127.1, time, 5.0))).isTrue();
        verify(sosLogs).save(7L, point);
    }

    @Test
    void sosStartCopiesRecentRedisPointsToPostgres() {
        GpsPoint point = new GpsPoint(10L, "session", 36.8, 127.1, Instant.now(), 5.0);
        when(store.recent(10L)).thenReturn(List.of(point));
        service.backfillRecent(10L, 7L);
        verify(sosLogs).saveAll(7L, List.of(point));
    }

    @Test
    void uploadRacingWithSosStartIsAlsoSavedToPostgres() {
        Instant time = Instant.now();
        GpsPoint point = new GpsPoint(10L, "session", 36.8, 127.1, time, 5.0);
        EmergencyEvent event = org.mockito.Mockito.mock(EmergencyEvent.class);
        when(event.getEmergencyId()).thenReturn(7L);
        when(emergencies.findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(
                10L, EmergencyEventRepository.OPEN_STATUSES))
                .thenReturn(Optional.empty(), Optional.of(event));
        when(store.append(point)).thenReturn(1);

        assertThat(service.append(10L, "session", new GpsRequest(36.8, 127.1, time, 5.0))).isTrue();
        verify(sosLogs).save(7L, point);
    }

    @Test
    void sosUploadKeepsDurableLogWhenRedisLatestFails() {
        OffsetDateTime time = OffsetDateTime.now(ZoneOffset.UTC);
        GpsPoint point = new GpsPoint(10L, "sos-7", 36.8, 127.1, time.toInstant(), 5.0);
        doThrow(new IllegalStateException("Redis unavailable")).when(store).updateEmergencyLatest(point);

        service.recordEmergency(10L, 7L, new LocationPoint(36.8, 127.1, 5.0, time));
        verify(sosLogs).save(7L, point);
    }

    @Test
    void rangeMergesCopiedRedisFixWithoutDuplicate() {
        Instant time = Instant.now().minusSeconds(10);
        Instant from = time.minusSeconds(1);
        Instant to = time.plusSeconds(1);
        GpsPoint redisPoint = new GpsPoint(10L, "session", 36.8, 127.1, time, 5.0);
        GpsPoint dbPoint = new GpsPoint(10L, "session", 36.8, 127.1,
                Instant.ofEpochMilli(time.toEpochMilli()), 5.0);
        when(sosLogs.range(10L, "session", from, to)).thenReturn(List.of(dbPoint));
        when(store.range(10L, "session", from, to)).thenReturn(List.of(redisPoint));

        assertThat(service.range(10L, 10L, "session", from, to)).containsExactly(dbPoint);
    }
}
