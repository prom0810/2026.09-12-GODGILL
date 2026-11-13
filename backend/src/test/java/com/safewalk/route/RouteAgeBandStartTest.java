package com.safewalk.route;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import com.safewalk.user.UserType;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteAgeBandStartTest {
    private final RouteRepository routes = mock(RouteRepository.class);
    private final RouteWriter writer = mock(RouteWriter.class);
    private final UserService users = mock(UserService.class);
    private final RouteService service = new RouteService(routes, mock(RoutePointRepository.class),
            mock(RouteSegmentRepository.class), mock(SegmentFacilityRepository.class), writer, users);

    private User user() {
        UserType type = mock(UserType.class);
        when(type.getUserTypeId()).thenReturn(2L);
        User user = new User(type, "이용자", "test@example.com", "hash", "010-1111-2222");
        when(users.getActiveUserForUpdate(1L)).thenReturn(user);
        return user;
    }

    private RouteRequests.Start request(RouteAgeBand band) {
        return new RouteRequests.Start(60, .5,
                List.of(new RouteRequests.Point(36.81, 127.14, null, null),
                        new RouteRequests.Point(36.811, 127.14, null, null)),
                List.of(new RouteRequests.Segment(.5, 0., List.of())), band);
    }

    @Test
    void staleAgeBandIsRejectedBeforeCancellingAnyExistingRoute() {
        user().setBirthDate(RouteAgeBand.today().minusYears(14));
        assertThatThrownBy(() -> service.start(1L, request(RouteAgeBand.UNDER_14)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("다시 계산");
        verifyNoInteractions(routes, writer);
    }

    @Test
    void storesTheAgeBandActuallyUsedByTheApp() {
        user().setBirthDate(RouteAgeBand.today().minusYears(10));
        service.start(1L, request(RouteAgeBand.UNDER_14));
        verify(writer).insert(eq(1L), eq(2L), any(RouteDraft.class), any(OffsetDateTime.class), eq(RouteAgeBand.UNDER_14));
    }

    @Test
    void keepsUnknownAgeBandForLegacyClientInsteadOfInventingASnapshot() {
        user();
        service.start(1L, request(null));
        verify(writer).insert(eq(1L), eq(2L), any(RouteDraft.class), any(OffsetDateTime.class), isNull());
    }
}
