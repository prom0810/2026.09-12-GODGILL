package com.safewalk.sensor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 심박 이상 SOS 동안의 심박 (Redis). SOS가 끝나면 bpm_log에 저장하고 지운다.
 *
 * 키: sensor:bpm:{emergencyId} 해시
 *  - start, startAt : SOS 발생 당시 심박 (워치가 감지 때 잰 값. 없으면 SOS 후 처음 받은 심박)
 *  - last,  lastAt  : 가장 최근에 받은 심박 → 종료 시 심박
 * 순서가 뒤바뀌어 도착한 배치는 시각을 비교해 무시한다 (Lua로 원자 처리).
 */
@Component
public class RedisSensorStore {

    private static final DefaultRedisScript<Long> RECORD = script("""
            if redis.call('HEXISTS', KEYS[1], 'start') == 0 then
              redis.call('HSET', KEYS[1], 'start', ARGV[1], 'startAt', ARGV[2])
            end
            local lastAt = redis.call('HGET', KEYS[1], 'lastAt')
            if not lastAt or tonumber(ARGV[4]) >= tonumber(lastAt) then
              redis.call('HSET', KEYS[1], 'last', ARGV[3], 'lastAt', ARGV[4])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[5])
            return 1
            """);

    /** start·last는 없을 수 있다 (워치가 심박을 못 잼) */
    public record Bpm(Integer start, Integer last) {
    }

    private final StringRedisTemplate redis;

    public RedisSensorStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** SOS 시작 시 발생 당시 심박 기록 (워치 감지 값). 이미 배치로 받은 값이 있으면 덮어쓴다 */
    public void begin(long emergencyId, int startBpm, long atMs, long ttlSeconds) {
        String key = key(emergencyId);
        redis.opsForHash().putAll(key, Map.of("start", Integer.toString(startBpm), "startAt", Long.toString(atMs)));
        redis.expire(key, java.time.Duration.ofSeconds(ttlSeconds));
    }

    /** 배치에서 꺼낸 심박. 시작 값이 없으면 first를 시작 값으로, last는 더 최근일 때만 갱신 */
    public void record(long emergencyId, int firstBpm, long firstAtMs, int lastBpm, long lastAtMs, long ttlSeconds) {
        redis.execute(RECORD, List.of(key(emergencyId)), Integer.toString(firstBpm), Long.toString(firstAtMs),
                Integer.toString(lastBpm), Long.toString(lastAtMs), Long.toString(ttlSeconds));
    }

    public Optional<Bpm> read(long emergencyId) {
        Map<Object, Object> m = redis.opsForHash().entries(key(emergencyId));
        if (m == null || m.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Bpm(toInt(m.get("start")), toInt(m.get("last"))));
    }

    public void clear(long emergencyId) {
        redis.delete(key(emergencyId));
    }

    private static Integer toInt(Object v) {
        try {
            return v == null ? null : Integer.valueOf((String) v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static DefaultRedisScript<Long> script(String source) {
        var s = new DefaultRedisScript<Long>();
        s.setScriptText(source);
        s.setResultType(Long.class);
        return s;
    }

    static String key(long emergencyId) {
        return "sensor:bpm:" + emergencyId;
    }
}
