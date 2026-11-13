package com.safewalk.location;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Latest fix and session track are separate Redis keys. All writes are atomic. */
@Component
public class RedisGpsStore {
    private static final long TRACK_MS = Duration.ofMinutes(60).toMillis();
    private static final DefaultRedisScript<Long> START = script("""
            local old = redis.call('GET', KEYS[1])
            if old then redis.call('DEL', 'gps:session:' .. old) end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', 4200)
            redis.call('SET', KEYS[2], ARGV[2], 'EX', 4200)
            redis.call('SET', KEYS[3], ARGV[2], 'EX', 4200)
            if old and redis.call('HGET', KEYS[4], 'session') == old then redis.call('DEL', KEYS[4]) end
            return 1
            """);
    private static final DefaultRedisScript<Long> APPEND = script("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return -1 end
            if tonumber(ARGV[3]) < tonumber(ARGV[4]) - 3600000 then return 0 end
            if ARGV[6] == '1' and #redis.call('ZRANGEBYSCORE', KEYS[3], ARGV[3], ARGV[3], 'LIMIT', 0, 1) > 0 then return 0 end
            local latestAt = redis.call('HGET', KEYS[2], 'at')
            if latestAt and tonumber(ARGV[3]) == tonumber(latestAt)
                and redis.call('HGET', KEYS[2], 'session') == ARGV[5] then
              if ARGV[6] == '1' then return 0 else return 1 end
            end
            if ARGV[6] == '1' then
              redis.call('ZADD', KEYS[3], ARGV[3], ARGV[2])
              redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', '(' .. (tonumber(ARGV[4]) - 3600000))
              redis.call('EXPIRE', KEYS[3], 3600)
            end
            redis.call('EXPIRE', KEYS[1], 4200)
            redis.call('EXPIRE', KEYS[4], 4200)
            redis.call('EXPIRE', KEYS[5], 4200)
            if not latestAt or tonumber(ARGV[3]) >= tonumber(latestAt) then
              redis.call('HSET', KEYS[2], 'at', ARGV[3], 'data', ARGV[2], 'session', ARGV[5])
              redis.call('EXPIRE', KEYS[2], 60)
              return 2
            end
            return 1
            """);
    private static final DefaultRedisScript<Long> CLOSE = script("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return -1 end
            redis.call('DEL', KEYS[1])
            if redis.call('GET', KEYS[2]) == ARGV[2] then redis.call('DEL', KEYS[2]) end
            if redis.call('HGET', KEYS[3], 'session') == ARGV[2] then redis.call('DEL', KEYS[3]) end
            return 1
            """);
    private static final DefaultRedisScript<Long> EMERGENCY_LATEST = script("""
            local latestAt = redis.call('HGET', KEYS[1], 'at')
            if not latestAt or tonumber(ARGV[2]) >= tonumber(latestAt) then
              redis.call('HSET', KEYS[1], 'at', ARGV[2], 'data', ARGV[1], 'session', ARGV[3])
              redis.call('EXPIRE', KEYS[1], 60)
              return 2
            end
            return 1
            """);
    private static final DefaultRedisScript<Long> CLOSE_EMERGENCY = script("""
            if redis.call('HGET', KEYS[1], 'session') == ARGV[1] then redis.call('DEL', KEYS[1]) end
            return 1
            """);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisGpsStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public String start(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        redis.execute(START, List.of(activeUser(userId), activeSession(sessionId), owner(sessionId), latestKey(userId)),
                sessionId, userId.toString());
        return sessionId;
    }

    /** 0=duplicate/expired, 1=history only, 2=history and newest fix. */
    public int append(GpsPoint point) {
        return append(point, true);
    }

    /** Validate the session and update only the live fix; SOS history goes to PostgreSQL. */
    public int appendLive(GpsPoint point) {
        return append(point, false);
    }

    private int append(GpsPoint point, boolean keepHistory) {
        long now = Instant.now().toEpochMilli();
        Long result = redis.execute(APPEND, List.of(activeSession(point.sessionId()), latestKey(point.userId()),
                        track(point.sessionId()), activeUser(point.userId()), owner(point.sessionId())),
                point.userId().toString(), json(point), String.valueOf(point.recordedAt().toEpochMilli()),
                String.valueOf(now), point.sessionId(), keepHistory ? "1" : "0");
        if (Objects.equals(result, -1L)) throw new LocationNotFoundException();
        return result == null ? 0 : result.intValue();
    }

    public int updateEmergencyLatest(GpsPoint point) {
        Long result = redis.execute(EMERGENCY_LATEST, List.of(latestKey(point.userId())),
                json(point), String.valueOf(point.recordedAt().toEpochMilli()), point.sessionId());
        return result == null ? 0 : result.intValue();
    }

    public void close(Long userId, String sessionId) {
        Long result = redis.execute(CLOSE, List.of(activeSession(sessionId), activeUser(userId), latestKey(userId)),
                userId.toString(), sessionId);
        if (Objects.equals(result, -1L)) throw new LocationNotFoundException();
    }

    public void closeEmergency(Long userId, Long emergencyId) {
        redis.execute(CLOSE_EMERGENCY, List.of(latestKey(userId)), "sos-" + emergencyId);
    }

    public boolean hasActiveSession(Long userId) {
        String sessionId = redis.opsForValue().get(activeUser(userId));
        return sessionId != null && Boolean.TRUE.equals(redis.hasKey(activeSession(sessionId)));
    }

    public Optional<GpsPoint> latest(Long userId) {
        String value = (String) redis.opsForHash().get(latestKey(userId), "data");
        return Optional.ofNullable(value).map(this::parse);
    }

    /** A snapshot of the current sharing session's Redis track and newest fix. */
    public List<GpsPoint> recent(Long userId) {
        String sessionId = redis.opsForValue().get(activeUser(userId));
        if (sessionId == null || !userId.toString().equals(redis.opsForValue().get(owner(sessionId)))) {
            return latest(userId).stream().toList();
        }
        var values = redis.opsForZSet().rangeByScore(track(sessionId),
                Instant.now().toEpochMilli() - TRACK_MS, Instant.now().toEpochMilli());
        List<GpsPoint> points = values == null ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(values.stream().map(this::parse).toList());
        latest(userId).filter(p -> points.stream().noneMatch(existing ->
                existing.sessionId().equals(p.sessionId()) && existing.recordedAt().equals(p.recordedAt())))
                .ifPresent(points::add);
        return points;
    }

    public List<GpsPoint> range(Long userId, String sessionId, Instant from, Instant to) {
        if (!userId.toString().equals(redis.opsForValue().get(owner(sessionId)))) {
            throw new LocationNotFoundException();
        }
        var values = redis.opsForZSet().rangeByScore(track(sessionId),
                Math.max(from.toEpochMilli(), Instant.now().toEpochMilli() - TRACK_MS), to.toEpochMilli());
        return values == null ? List.of() : values.stream().map(this::parse).toList();
    }

    private String json(GpsPoint point) {
        try { return mapper.writeValueAsString(point); }
        catch (JsonProcessingException e) { throw new IllegalStateException("GPS 로그 직렬화 실패", e); }
    }

    private GpsPoint parse(String value) {
        try { return mapper.readValue(value, GpsPoint.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("GPS 로그 읽기 실패", e); }
    }

    private static DefaultRedisScript<Long> script(String source) {
        var script = new DefaultRedisScript<Long>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }

    private static String owner(String sid) { return "gps:owner:" + sid; }
    private static String activeSession(String sid) { return "gps:session:" + sid; }
    private static String activeUser(Long uid) { return "gps:active-user:" + uid; }
    private static String latestKey(Long uid) { return "gps:latest:" + uid; }
    private static String track(String sid) { return "gps:track:" + sid; }
}
