package com.safewalk.location;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable location history for an SOS, including the Redis history copied at its start. */
@Repository
public class SosLocationLogRepository {
    private static final String INSERT = """
            INSERT INTO sos_log (emergency_id, session_id, lat, lng, accuracy, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (emergency_id, session_id, recorded_at) DO NOTHING
            """;
    private final JdbcTemplate jdbc;

    public SosLocationLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean save(Long emergencyId, GpsPoint point) {
        return jdbc.update(INSERT, emergencyId, point.sessionId(), point.lat(), point.lng(), point.accuracy(),
                Timestamp.from(point.recordedAt())) > 0;
    }

    public void saveAll(Long emergencyId, List<GpsPoint> points) {
        jdbc.batchUpdate(INSERT, points, 200, (statement, point) -> {
            statement.setLong(1, emergencyId);
            statement.setString(2, point.sessionId());
            statement.setDouble(3, point.lat());
            statement.setDouble(4, point.lng());
            if (point.accuracy() == null) statement.setNull(5, Types.DOUBLE);
            else statement.setDouble(5, point.accuracy());
            statement.setTimestamp(6, Timestamp.from(point.recordedAt()));
        });
    }

    public List<GpsPoint> range(Long userId, String sessionId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT e.user_id, l.session_id, l.lat, l.lng, l.recorded_at, l.accuracy
                FROM sos_log l JOIN emergency_event e ON e.emergency_id = l.emergency_id
                WHERE e.user_id = ? AND l.session_id = ? AND l.recorded_at BETWEEN ? AND ?
                ORDER BY l.recorded_at, l.sos_log_id
                """, (rs, row) -> new GpsPoint(rs.getLong("user_id"), rs.getString("session_id"),
                        rs.getDouble("lat"), rs.getDouble("lng"),
                        rs.getTimestamp("recorded_at").toInstant(),
                        (Double) rs.getObject("accuracy")),
                userId, sessionId, Timestamp.from(from), Timestamp.from(to));
    }

    /** 한 세션의 가장 이른 위치 (SOS 발생 위치 조회용) */
    public Optional<GpsPoint> first(Long emergencyId, String sessionId) {
        return jdbc.query("""
                SELECT e.user_id, l.session_id, l.lat, l.lng, l.recorded_at, l.accuracy
                FROM sos_log l JOIN emergency_event e ON e.emergency_id = l.emergency_id
                WHERE l.emergency_id = ? AND l.session_id = ? AND l.recorded_at IS NOT NULL
                ORDER BY l.recorded_at, l.sos_log_id
                LIMIT 1
                """, (rs, row) -> new GpsPoint(rs.getLong("user_id"), rs.getString("session_id"),
                        rs.getDouble("lat"), rs.getDouble("lng"),
                        rs.getTimestamp("recorded_at").toInstant(),
                        (Double) rs.getObject("accuracy")), emergencyId, sessionId).stream().findFirst();
    }

    public Optional<GpsPoint> latest(Long emergencyId) {
        return jdbc.query("""
                SELECT e.user_id, l.session_id, l.lat, l.lng, l.recorded_at, l.accuracy
                FROM sos_log l JOIN emergency_event e ON e.emergency_id = l.emergency_id
                WHERE l.emergency_id = ? AND l.recorded_at IS NOT NULL
                ORDER BY l.recorded_at DESC, l.sos_log_id DESC
                LIMIT 1
                """, (rs, row) -> new GpsPoint(rs.getLong("user_id"), rs.getString("session_id"),
                        rs.getDouble("lat"), rs.getDouble("lng"),
                        rs.getTimestamp("recorded_at").toInstant(),
                        (Double) rs.getObject("accuracy")), emergencyId).stream().findFirst();
    }
}
