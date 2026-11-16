package com.safewalk.sensor;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 심박 이상 SOS 종료 시 bpm_log에 발생 당시·종료 시 심박을 1행 저장한다.
 *
 * - bpm_start: 워치가 감지할 때 잰 심박 (없으면 SOS 후 처음 받은 심박)
 * - bpm_end  : SOS 동안 마지막으로 받은 심박
 * 둘 중 하나라도 없으면(워치 미착용·전송 실패) 저장하지 않고 로그만 남긴다 (두 컬럼 NOT NULL).
 * SOS 종료 커밋 직후(afterCommit) 호출되므로 새 트랜잭션으로 저장하고, 실패해도 SOS 종료에는 영향이 없다.
 */
@Component
public class BpmLogWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BpmLogWriter.class);
    private static final String INSERT = "INSERT INTO bpm_log (emergency_id, bpm_start, bpm_end) VALUES (?, ?, ?)";

    private final RedisSensorStore store;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public BpmLogWriter(RedisSensorStore store, JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.store = store;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return 저장했으면 true */
    public boolean save(long emergencyId) {
        try {
            Optional<RedisSensorStore.Bpm> bpm = store.read(emergencyId);
            if (bpm.isEmpty() || bpm.get().start() == null || bpm.get().last() == null) {
                LOGGER.warn("bpm_log 저장 생략: 심박 값 없음 (emergencyId={}, 값={})", emergencyId, bpm.orElse(null));
                return false;
            }
            int start = bpm.get().start();
            int end = bpm.get().last();
            tx.executeWithoutResult(s -> jdbc.update(INSERT, emergencyId, (short) start, (short) end));
            LOGGER.info("bpm_log 저장 (emergencyId={}): 발생 {} → 종료 {}", emergencyId, start, end);
            return true;
        } finally {
            try {
                store.clear(emergencyId);
            } catch (RuntimeException e) {
                LOGGER.warn("Redis 심박 값 삭제 실패 (emergencyId={}) — TTL로 정리됨", emergencyId, e);
            }
        }
    }
}
