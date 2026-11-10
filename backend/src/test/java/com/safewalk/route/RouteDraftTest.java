package com.safewalk.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteDraftTest {

    private static RouteRequests.Point point(double lat, double lng, String name) {
        return new RouteRequests.Point(lat, lng, name, null);
    }

    private static RouteRequests.Segment segment(double safety) {
        return new RouteRequests.Segment(safety, null, null);
    }

    private static RouteRequests.Start threePointRoute(List<RouteRequests.Segment> segments) {
        return new RouteRequests.Start(600, 0.7,
                List.of(point(36.8100, 127.1467, "천안역"),
                        point(36.8105, 127.1467, "무시되는 이름"),
                        point(36.8110, 127.1467, "도착")),
                segments);
    }

    @Test
    void assignsPointTypesByOrderAndKeepsNamesOnlyAtEnds() {
        RouteDraft draft = RouteDraft.from(threePointRoute(List.of(segment(0.8), segment(0.4))));

        assertThat(draft.points()).extracting(RouteDraft.PointRow::type)
                .containsExactly(RoutePointType.START, RoutePointType.WAYPOINT, RoutePointType.END);
        assertThat(draft.points().get(0).name()).isEqualTo("천안역");
        assertThat(draft.points().get(1).name()).isNull();
        assertThat(draft.points().get(2).name()).isEqualTo("도착");
    }

    @Test
    void computesDistancesOnServerAndDefaultsOptionalScores() {
        RouteDraft draft = RouteDraft.from(threePointRoute(List.of(segment(0.8), segment(0.4))));

        // 위도 0.0005도 ≈ 55.6m, 두 구간 합 ≈ 111.2m
        assertThat(draft.segments().get(0).distance()).isCloseTo(55.6, within(0.5));
        assertThat(draft.totalDistance()).isCloseTo(111.2, within(1.0));
        assertThat(draft.segments().get(0).riskScore()).isZero();
        assertThat(draft.segments().get(0).facilities()).isEmpty();
        assertThat(draft.facilityCount()).isZero();
    }

    @Test
    void acceptsFacilitiesAndCountsThem() {
        RouteRequests.Facility cctv = new RouteRequests.Facility("cctv", 36.8102, 127.1468, 2, 35.2, 0.115);
        RouteRequests.Facility light = new RouteRequests.Facility("STREET_LIGHT", 36.8103, 127.1466, null, 20.0, 0.08);
        RouteDraft draft = RouteDraft.from(threePointRoute(List.of(
                new RouteRequests.Segment(0.8, 0.0, List.of(cctv, light)),
                new RouteRequests.Segment(0.3, 0.42, List.of()))));

        assertThat(draft.facilityCount()).isEqualTo(2);
        RouteDraft.FacilityRow first = draft.segments().get(0).facilities().get(0);
        assertThat(first.type()).isEqualTo(SegmentFacilityType.CCTV);
        assertThat(first.quantity()).isEqualTo(2);
        assertThat(draft.segments().get(0).facilities().get(1).quantity()).isEqualTo(1);
        assertThat(draft.segments().get(1).riskScore()).isEqualTo(0.42);
    }

    @Test
    void rejectsSegmentCountMismatch() {
        assertThatThrownBy(() -> RouteDraft.from(threePointRoute(List.of(segment(0.5)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("segments 개수");
    }

    @Test
    void rejectsOutOfRangeScoresAndUnknownFacility() {
        assertThatThrownBy(() -> RouteDraft.from(threePointRoute(List.of(segment(1.2), segment(0.5)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouteDraft.from(threePointRoute(List.of(
                new RouteRequests.Segment(0.5, -0.1, null), segment(0.5)))))
                .isInstanceOf(IllegalArgumentException.class);
        RouteRequests.Facility unknown = new RouteRequests.Facility("BENCH", 36.81, 127.14, 1, 1.0, 0.1);
        assertThatThrownBy(() -> RouteDraft.from(threePointRoute(List.of(
                new RouteRequests.Segment(0.5, 0.0, List.of(unknown)), segment(0.5)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시설 종류");
    }

    @Test
    void rejectsTooManyPointsAndBadCoordinates() {
        List<RouteRequests.Point> points = new ArrayList<>();
        List<RouteRequests.Segment> segments = new ArrayList<>();
        for (int i = 0; i <= RouteDraft.MAX_POINTS; i++) {
            points.add(point(36.81 + i * 0.00001, 127.14, null));
            if (i > 0) {
                segments.add(segment(0.5));
            }
        }
        assertThatThrownBy(() -> RouteDraft.from(new RouteRequests.Start(60, 0.5, points, segments)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대");

        assertThatThrownBy(() -> RouteDraft.from(new RouteRequests.Start(60, 0.5,
                List.of(point(91, 127, null), point(36.8, 127.1, null)), List.of(segment(0.5)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresEstimatedTimeAndSafetyScore() {
        List<RouteRequests.Point> points = List.of(point(36.81, 127.14, null), point(36.811, 127.14, null));
        assertThatThrownBy(() -> RouteDraft.from(new RouteRequests.Start(null, 0.5, points, List.of(segment(0.5)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouteDraft.from(new RouteRequests.Start(60, null, points, List.of(segment(0.5)))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
