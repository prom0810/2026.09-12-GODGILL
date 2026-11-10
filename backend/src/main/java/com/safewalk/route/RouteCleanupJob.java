package com.safewalk.route;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 경로 데이터 자동 정리. 설정은 {@link RouteProperties}.
 *
 * 1) 방치된 안내 자동 취소 (10분마다)
 *    IN_PROGRESS로 staleHours(기본 6시간)가 지난 경로 → CANCELLED.
 *    앱이 꺼져 도착/취소를 못 보낸 경우를 정리한다.
 *
 * 2) 보관 기간 정리 (매일 04:10, 한국 시각)
 *    a. deleteAfterDays(기본 365일) 지난 경로 → 완전히 삭제 (지점·구간·시설은 FK CASCADE로 함께)
 *    b. detailRetentionDays(기본 65일) 지난 경로 → 구간·시설과 경유 지점(WAYPOINT)만 삭제.
 *       경로 요약(route)과 출발·도착 지점(START/END)은 남겨서 기록 목록에 "A → B"가 계속 보이게 한다.
 *
 *    제외: 긴급 사건(emergency_event)이나 웨어러블 측정 기록(heart_rate_record, motion_record)이
 *    그 경로·구간을 참조하면 지우지 않는다. 사건 당시 경로는 근거 자료이고, DB FK도 ON DELETE RESTRICT라서다.
 *    (웨어러블 기록 보관 정책이 생기면 그 기록을 먼저 지운 뒤 이 정리 대상이 된다.)
 *
 * 한 번에 batchSize개씩, 묶음마다 별도 트랜잭션으로 처리해 긴 잠금을 피한다.
 * 같은 작업을 여러 번 실행해도 결과가 같아서, 서버가 여러 대여도 문제없다.
 */
@Component
public class RouteCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RouteCleanupJob.class);
    /** 한 번 실행에서 처리하는 최대 묶음 수 (batchSize 500 × 200 = 10만 건) */
    private static final int MAX_BATCHES_PER_RUN = 200;

    /** 긴급 사건·웨어러블 기록이 이 경로(r)나 그 구간을 참조하지 않음 */
    private static final String NOT_REFERENCED = """
             AND NOT EXISTS (SELECT 1 FROM emergency_event e WHERE e.route_id = r.route_id)
             AND NOT EXISTS (
                 SELECT 1 FROM route_point p
                 JOIN route_segment s ON s.start_point_id = p.point_id
                 WHERE p.route_id = r.route_id
                   AND (EXISTS (SELECT 1 FROM emergency_event e WHERE e.segment_id = s.segment_id)
                     OR EXISTS (SELECT 1 FROM heart_rate_record h WHERE h.segment_id = s.segment_id)
                     OR EXISTS (SELECT 1 FROM motion_record m WHERE m.segment_id = s.segment_id)))
            """;

    private static final String CANCEL_STALE = """
            UPDATE route SET status = 'CANCELLED'
            WHERE status = 'IN_PROGRESS' AND created_at < ?
            """;

    private static final String DELETE_CANDIDATES = """
            SELECT r.route_id FROM route r
            WHERE r.created_at < ? AND r.status <> 'IN_PROGRESS'
            """ + NOT_REFERENCED + """
            ORDER BY r.route_id
            LIMIT ?
            """;

    private static final String DELETE_ROUTES = "DELETE FROM route WHERE route_id = ANY (?)";

    /** 아직 상세(경유 지점 또는 구간)가 남아 있는 경로만 */
    private static final String PURGE_CANDIDATES = """
            SELECT r.route_id FROM route r
            WHERE r.created_at < ? AND r.status <> 'IN_PROGRESS'
              AND EXISTS (
                  SELECT 1 FROM route_point p
                  WHERE p.route_id = r.route_id
                    AND (p.point_type = 'WAYPOINT'
                      OR EXISTS (SELECT 1 FROM route_segment s WHERE s.start_point_id = p.point_id)))
            """ + NOT_REFERENCED + """
            ORDER BY r.route_id
            LIMIT ?
            """;

    /** 구간 삭제 → segment_facility는 FK CASCADE로 함께 삭제 */
    private static final String PURGE_SEGMENTS = """
            DELETE FROM route_segment s
            USING route_point p
            WHERE s.start_point_id = p.point_id AND p.route_id = ANY (?)
            """;

    private static final String PURGE_WAYPOINTS = """
            DELETE FROM route_point WHERE route_id = ANY (?) AND point_type = 'WAYPOINT'
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final RouteProperties properties;

    public RouteCleanupJob(JdbcTemplate jdbc, TransactionTemplate tx, RouteProperties properties) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.properties = properties;
    }

    public record Result(int deleted, int purged) {
    }

    @Scheduled(fixedDelayString = "${route.stale-check-interval-ms:600000}", initialDelay = 60_000)
    public void cancelStaleRoutes() {
        try {
            int cancelled = cancelStale(Instant.now());
            if (cancelled > 0) {
                log.info("[route-cleanup] 방치된 안내 {}건 자동 취소", cancelled);
            }
        } catch (RuntimeException e) {
            log.warn("[route-cleanup] 방치된 안내 자동 취소 실패: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${route.cleanup-cron:0 10 4 * * *}", zone = "Asia/Seoul")
    public void cleanupOldRoutes() {
        try {
            Result result = runRetention(Instant.now());
            log.info("[route-cleanup] 완전 삭제 {}건, 상세 정리 {}건", result.deleted(), result.purged());
        } catch (RuntimeException e) {
            log.warn("[route-cleanup] 보관 기간 정리 실패 (다음 실행에서 이어서 처리): {}", e.getMessage());
        }
    }

    int cancelStale(Instant now) {
        Timestamp cutoff = Timestamp.from(now.minus(Duration.ofHours(properties.staleHours())));
        Integer count = tx.execute(status -> jdbc.update(CANCEL_STALE, cutoff));
        return count == null ? 0 : count;
    }

    /** 완전 삭제 먼저 (곧 지울 경로를 굳이 정리하지 않게), 그다음 상세 정리 */
    Result runRetention(Instant now) {
        Timestamp deleteCutoff = Timestamp.from(now.minus(Duration.ofDays(properties.deleteAfterDays())));
        Timestamp purgeCutoff = Timestamp.from(now.minus(Duration.ofDays(properties.detailRetentionDays())));

        int deleted = inBatches(DELETE_CANDIDATES, deleteCutoff, ids -> jdbc.update(DELETE_ROUTES,
                ps -> ps.setArray(1, bigintArray(ps.getConnection(), ids))));
        int purged = inBatches(PURGE_CANDIDATES, purgeCutoff, ids -> {
            jdbc.update(PURGE_SEGMENTS, ps -> ps.setArray(1, bigintArray(ps.getConnection(), ids)));
            jdbc.update(PURGE_WAYPOINTS, ps -> ps.setArray(1, bigintArray(ps.getConnection(), ids)));
        });
        return new Result(deleted, purged);
    }

    private int inBatches(String candidatesSql, Timestamp cutoff, BatchAction action) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            Integer done = tx.execute(status -> {
                List<Long> ids = jdbc.queryForList(candidatesSql, Long.class, cutoff, properties.batchSize());
                if (!ids.isEmpty()) {
                    action.run(ids);
                }
                return ids.size();
            });
            int count = done == null ? 0 : done;
            total += count;
            if (count < properties.batchSize()) {
                break;
            }
        }
        return total;
    }

    private static Array bigintArray(java.sql.Connection connection, List<Long> ids) throws java.sql.SQLException {
        return connection.createArrayOf("bigint", ids.toArray());
    }

    @FunctionalInterface
    private interface BatchAction {
        void run(List<Long> ids);
    }
}
