package com.safewalk.route;

import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteSafetyProfileService {
    private final UserService users;
    private final JdbcTemplate jdbc;

    public RouteSafetyProfileService(UserService users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public RouteSafetyProfile get(Long userId) {
        User user = users.getActiveUser(userId);
        // 기존 MINOR에는 14~18세도 포함되므로 임의로 연령대를 추정하지 않는다.
        if (user.getBirthDate() == null) {
            return new RouteSafetyProfile(null, true, Map.of());
        }
        RouteAgeBand band = RouteAgeBand.from(user.getBirthDate(), RouteAgeBand.today());
        Map<String, Double> weights = new LinkedHashMap<>();
        jdbc.query("SELECT facility_type, weight FROM route_facility_weight WHERE age_band = ? ORDER BY facility_type",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    double weight = rs.getDouble("weight");
                    if (!Double.isFinite(weight) || weight < 0) {
                        throw new IllegalStateException("경로 시설 가중치 설정이 올바르지 않습니다.");
                    }
                    weights.put(rs.getString("facility_type"), weight);
                }, band.name());
        for (SegmentFacilityType type : SegmentFacilityType.values()) {
            if (!weights.containsKey(type.name())) {
                throw new IllegalStateException("경로 시설 가중치가 누락되었습니다: " + type.name());
            }
        }
        return new RouteSafetyProfile(band, false, Map.copyOf(weights));
    }
}
