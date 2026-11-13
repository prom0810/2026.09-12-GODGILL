package com.safewalk.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import com.safewalk.user.UserType;
import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class RouteSafetyProfileServiceTest {
    private final UserService users = mock(UserService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RouteSafetyProfileService service = new RouteSafetyProfileService(users, jdbc);

    private User user() {
        User user = new User(new UserType("MINOR"), "이용자", "test@example.com", "hash", "010-1111-2222");
        when(users.getActiveUser(1L)).thenReturn(user);
        return user;
    }

    private void rows(RouteAgeBand band, Map<String, Double> weights) {
        doAnswer(invocation -> {
            RowCallbackHandler callback = invocation.getArgument(1);
            for (var entry : weights.entrySet()) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("facility_type")).thenReturn(entry.getKey());
                when(rs.getDouble("weight")).thenReturn(entry.getValue());
                callback.processRow(rs);
            }
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(band.name()));
    }

    @Test
    void legacyMinorMustSupplyBirthDateRatherThanBeingAssumedUnder14() {
        user();
        RouteSafetyProfile profile = service.get(1L);
        assertThat(profile.birthDateRequired()).isTrue();
        assertThat(profile.ageBand()).isNull();
        assertThat(profile.facilityWeights()).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void fourteenYearOldMinorUsesAge14PlusDatabaseValues() {
        User user = user();
        user.setBirthDate(RouteAgeBand.today().minusYears(16));
        Map<String, Double> values = Map.of("CCTV", .17, "STREET_LIGHT", .12, "POLICE", .3,
                "EMERGENCY_BELL", .2, "CONVENIENCE_STORE", .1, "FIRE_STATION", .2);
        rows(RouteAgeBand.AGE_14_PLUS, values);
        RouteSafetyProfile profile = service.get(1L);
        assertThat(profile.birthDateRequired()).isFalse();
        assertThat(profile.ageBand()).isEqualTo(RouteAgeBand.AGE_14_PLUS);
        assertThat(profile.facilityWeights()).isEqualTo(values);
    }

    @Test
    void missingOrNonFiniteConfigurationIsNotSilentlyReplacedWithAnotherAgeBand() {
        user().setBirthDate(RouteAgeBand.today().minusYears(10));
        rows(RouteAgeBand.UNDER_14, Map.of("CCTV", .2));
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(IllegalStateException.class).hasMessageContaining("누락");
        rows(RouteAgeBand.UNDER_14, Map.of("CCTV", Double.NaN));
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(IllegalStateException.class).hasMessageContaining("올바르지");
    }
}
