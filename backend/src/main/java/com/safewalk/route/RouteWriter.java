package com.safewalk.route;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * 경로 1건(경로 + 지점 + 구간 + 시설)을 적은 왕복으로 저장한다.
 *
 * 왜 JPA save()가 아닌가
 *  - IDENTITY 키는 INSERT를 해야 ID가 나오므로 Hibernate가 행마다 INSERT를 따로 보낸다.
 *    경로 1건이 약 200~250행이라, 원격 DB(Supabase)에서는 저장에만 수 초가 걸린다.
 *
 * 방식
 *  1. 각 테이블의 IDENTITY 시퀀스에서 필요한 개수만큼 ID를 한 번의 쿼리로 미리 받는다
 *     (SELECT nextval(...) FROM generate_series(1, n)). DB 스키마는 바꾸지 않는다.
 *  2. 받은 ID로 FK까지 채운 뒤 테이블별로 batch INSERT 한다.
 *     드라이버 옵션 reWriteBatchedInserts=true(application.yml)라 batch가 여러 행 INSERT 문으로 합쳐진다.
 *  → 경로 1건 저장 = ID 조회 1번 + INSERT 4종 (지점이 많으면 128행 단위로 나뉨)
 *
 * 시퀀스는 증가폭 1 그대로 쓰므로, 다른 곳에서 ID 없이 INSERT 해도 번호가 겹치지 않는다.
 * 호출하는 쪽(RouteService)의 트랜잭션 안에서 실행되어 실패하면 전부 롤백된다.
 */
@Component
public class RouteWriter {

    private static final String ALLOCATE_IDS = """
            SELECT 'R' AS kind, nextval(pg_get_serial_sequence('route', 'route_id')) AS id
            UNION ALL
            SELECT 'P', nextval(pg_get_serial_sequence('route_point', 'point_id')) FROM generate_series(1, ?)
            UNION ALL
            SELECT 'S', nextval(pg_get_serial_sequence('route_segment', 'segment_id')) FROM generate_series(1, ?)
            UNION ALL
            SELECT 'F', nextval(pg_get_serial_sequence('segment_facility', 'segment_facility_id')) FROM generate_series(1, ?)
            """;

    private static final String INSERT_ROUTE = """
            INSERT INTO route (route_id, user_id, user_type_id, total_distance, estimated_time, safety_score,
                               status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?)
            """;

    private static final String INSERT_POINT = """
            INSERT INTO route_point (point_id, route_id, seq, point_type, name, address, lat, lng)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_SEGMENT = """
            INSERT INTO route_segment (segment_id, start_point_id, end_point_id, distance, safety_score, risk_score)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_FACILITY = """
            INSERT INTO segment_facility (segment_facility_id, segment_id, facility_type, lat, lng, quantity,
                                          distance, applied_score)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public RouteWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return 새 route_id */
    public long insert(long userId, long userTypeId, RouteDraft draft, OffsetDateTime createdAt) {
        AllocatedIds ids = allocate(draft.points().size(), draft.segments().size(), draft.facilityCount());
        long routeId = ids.route;

        jdbc.update(INSERT_ROUTE, routeId, userId, userTypeId, draft.totalDistance(), draft.estimatedTime(),
                draft.safetyScore(), Timestamp.from(createdAt.toInstant()));

        List<RouteDraft.PointRow> points = draft.points();
        long[] pointIds = ids.points;
        jdbc.batchUpdate(INSERT_POINT, points, points.size(), (ps, p) -> {
            ps.setLong(1, pointIds[p.seq()]);
            ps.setLong(2, routeId);
            ps.setInt(3, p.seq());
            ps.setString(4, p.type().name());
            setNullableString(ps, 5, p.name());
            setNullableString(ps, 6, p.address());
            ps.setDouble(7, p.lat());
            ps.setDouble(8, p.lng());
        });

        List<RouteDraft.SegmentRow> segments = draft.segments();
        long[] segmentIds = ids.segments;
        jdbc.batchUpdate(INSERT_SEGMENT, segments, segments.size(), (ps, s) -> {
            ps.setLong(1, segmentIds[s.seq()]);
            ps.setLong(2, pointIds[s.seq()]);
            ps.setLong(3, pointIds[s.seq() + 1]);
            ps.setDouble(4, s.distance());
            ps.setDouble(5, s.safetyScore());
            ps.setDouble(6, s.riskScore());
        });

        if (draft.facilityCount() > 0) {
            List<Object[]> rows = new ArrayList<>(draft.facilityCount());
            int next = 0;
            for (RouteDraft.SegmentRow s : segments) {
                for (RouteDraft.FacilityRow f : s.facilities()) {
                    rows.add(new Object[]{ids.facilities[next++], segmentIds[s.seq()], f});
                }
            }
            jdbc.batchUpdate(INSERT_FACILITY, rows, rows.size(), (ps, row) -> {
                RouteDraft.FacilityRow f = (RouteDraft.FacilityRow) row[2];
                ps.setLong(1, (Long) row[0]);
                ps.setLong(2, (Long) row[1]);
                ps.setString(3, f.type().name());
                ps.setDouble(4, f.lat());
                ps.setDouble(5, f.lng());
                ps.setInt(6, f.quantity());
                ps.setDouble(7, f.distance());
                ps.setDouble(8, f.appliedScore());
            });
        }
        return routeId;
    }

    private AllocatedIds allocate(int pointCount, int segmentCount, int facilityCount) {
        AllocatedIds ids = new AllocatedIds(pointCount, segmentCount, facilityCount);
        int[] cursor = new int[3];
        jdbc.query(ALLOCATE_IDS, (RowCallbackHandler) rs -> {
            long id = rs.getLong("id");
            switch (rs.getString("kind")) {
                case "R" -> ids.route = id;
                case "P" -> ids.points[cursor[0]++] = id;
                case "S" -> ids.segments[cursor[1]++] = id;
                case "F" -> ids.facilities[cursor[2]++] = id;
                default -> throw new IllegalStateException("알 수 없는 ID 종류");
            }
        }, pointCount, segmentCount, facilityCount);
        if (ids.route == 0 || cursor[0] != pointCount || cursor[1] != segmentCount || cursor[2] != facilityCount) {
            throw new IllegalStateException("경로 ID를 미리 받지 못했습니다.");
        }
        return ids;
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static final class AllocatedIds {
        long route;
        final long[] points;
        final long[] segments;
        final long[] facilities;

        AllocatedIds(int points, int segments, int facilities) {
            this.points = new long[points];
            this.segments = new long[segments];
            this.facilities = new long[facilities];
        }
    }
}
