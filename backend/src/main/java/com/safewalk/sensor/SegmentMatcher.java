package com.safewalk.sensor;

import java.util.List;

/**
 * 좌표를 경로 구간 위의 위치로 바꾼다 (가장 가까운 구간 + 그 구간 시작점부터 거리).
 * 천안역 주변 1~2km 범위라 구간마다 시작점 기준 평면 근사(equirectangular)로 충분하다.
 */
public final class SegmentMatcher {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /** 구간 하나. distance는 DB route_segment.distance(m) */
    public record Segment(long segmentId, double startLat, double startLng, double endLat, double endLng,
                          double distance) {
    }

    /** segmentId 구간의 시작점에서 along(m) 지점. offset은 구간 선분까지 수직 거리(m) */
    public record Match(long segmentId, double along, double offset) {
    }

    private final List<Segment> segments;

    public SegmentMatcher(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("경로 구간이 없습니다.");
        }
        this.segments = List.copyOf(segments);
    }

    public Segment first() {
        return segments.get(0);
    }

    public Match match(double lat, double lng) {
        Match best = null;
        for (Segment s : segments) {
            Match m = project(s, lat, lng);
            if (best == null || m.offset() < best.offset()) {
                best = m;
            }
        }
        return best;
    }

    static Match project(Segment s, double lat, double lng) {
        double cos = Math.cos(Math.toRadians(s.startLat()));
        double bx = Math.toRadians(s.endLng() - s.startLng()) * cos * EARTH_RADIUS_M;
        double by = Math.toRadians(s.endLat() - s.startLat()) * EARTH_RADIUS_M;
        double px = Math.toRadians(lng - s.startLng()) * cos * EARTH_RADIUS_M;
        double py = Math.toRadians(lat - s.startLat()) * EARTH_RADIUS_M;
        double len2 = bx * bx + by * by;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * bx + py * by) / len2));
        double dx = px - t * bx;
        double dy = py - t * by;
        double along = Math.max(0, Math.min(s.distance(), t * s.distance()));
        return new Match(s.segmentId(), along, Math.sqrt(dx * dx + dy * dy));
    }
}
