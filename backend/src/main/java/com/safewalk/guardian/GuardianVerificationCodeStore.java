package com.safewalk.guardian;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 보호자 확인 인증번호 보관소 (서버 메모리).
 *
 * 규칙 (account-recovery-plan의 인증번호 규칙과 같다)
 *  - 6자리 숫자, 발급 후 {@link #TTL} 동안 유효
 *  - 같은 보호자에게 {@link #RESEND_INTERVAL} 안에 다시 발급할 수 없다 → {@link VerificationTooSoonException}(429)
 *  - 새로 발급하면 이전 번호는 무효
 *  - {@link #MAX_ATTEMPTS}번 틀리면 그 번호는 무효 (다시 발급받아야 함)
 *  - 확인에 성공하면 지운다 (1회용)
 *
 * 인증번호는 DB에 저장하지 않는다. 서버를 다시 시작하면 발급된 번호는 모두 사라진다(다시 발급하면 됨).
 * 서버 1대 기준 구현이라 MSA 전환 시 Redis(TTL 키)로 옮긴다.
 */
@Component
public class GuardianVerificationCodeStore {

    public static final Duration TTL = Duration.ofMinutes(3);
    public static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    public static final int MAX_ATTEMPTS = 5;
    public static final int CODE_LENGTH = 6;
    /** 만료 후 이 시간이 지난 번호는 다음 발급 때 정리한다. 그 전까지는 확인 시 "만료"로 안내된다 */
    private static final Duration STALE_AFTER_EXPIRY = Duration.ofMinutes(30);

    /** 확인 결과. 실패하면 reason과 남은 시도 횟수를 알려 준다 */
    public enum Result {
        OK,
        /** 발급된 번호가 없음 (발급 전이거나, 이미 사용·무효 처리됨) */
        NOT_REQUESTED,
        EXPIRED,
        MISMATCH,
        /** 5번 틀려서 무효가 됨 (이번 시도로 무효가 된 경우 포함) */
        LOCKED
    }

    public record Issued(String code, Instant expiresAt, Instant resendAvailableAt) {
    }

    public record Check(Result result, int remainingAttempts) {
    }

    private static final class Entry {
        private final String code;
        private final Instant issuedAt;
        private final Instant expiresAt;
        private int failures;
        private boolean locked;

        private Entry(String code, Instant issuedAt) {
            this.code = code;
            this.issuedAt = issuedAt;
            this.expiresAt = issuedAt.plus(TTL);
        }
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    /**
     * 새 인증번호 발급. 이전 번호는 무효가 된다.
     *
     * @throws VerificationTooSoonException 마지막 발급 후 1분이 안 지났을 때
     */
    public Issued issue(Long guardianId, Instant now) {
        // 만료된 지 오래된 번호는 정리한다 (메모리가 계속 늘지 않게)
        Instant staleBefore = now.minus(STALE_AFTER_EXPIRY);
        entries.values().removeIf(e -> e.expiresAt.isBefore(staleBefore));

        Entry[] created = new Entry[1];
        entries.compute(guardianId, (id, old) -> {
            if (old != null) {
                Instant resendAt = old.issuedAt.plus(RESEND_INTERVAL);
                if (now.isBefore(resendAt)) {
                    long seconds = (Duration.between(now, resendAt).toMillis() + 999) / 1000; // 올림
                    throw new VerificationTooSoonException(
                            "인증번호는 1분에 한 번만 보낼 수 있습니다. " + seconds + "초 뒤에 다시 시도해 주세요.", seconds);
                }
            }
            created[0] = new Entry(newCode(), now);
            return created[0];
        });
        Entry entry = created[0];
        return new Issued(entry.code, entry.expiresAt, entry.issuedAt.plus(RESEND_INTERVAL));
    }

    /** 입력한 번호 확인. 맞으면 번호를 지운다. */
    public Check verify(Long guardianId, String code, Instant now) {
        Check[] result = new Check[1];
        entries.computeIfPresent(guardianId, (id, entry) -> {
            if (entry.locked) {
                result[0] = new Check(Result.LOCKED, 0);
                return entry;
            }
            if (!now.isBefore(entry.expiresAt)) {
                result[0] = new Check(Result.EXPIRED, 0);
                return entry; // 재발송 1분 제한 판단에 issuedAt이 필요해서 남겨 둔다
            }
            if (entry.code.equals(code)) {
                result[0] = new Check(Result.OK, MAX_ATTEMPTS - entry.failures);
                return null;
            }
            entry.failures++;
            if (entry.failures >= MAX_ATTEMPTS) {
                entry.locked = true;
                result[0] = new Check(Result.LOCKED, 0);
            } else {
                result[0] = new Check(Result.MISMATCH, MAX_ATTEMPTS - entry.failures);
            }
            return entry;
        });
        return result[0] != null ? result[0] : new Check(Result.NOT_REQUESTED, 0);
    }

    /** 보호자 삭제 등으로 더 이상 필요 없는 번호를 지운다 */
    public void discard(Long guardianId) {
        entries.remove(guardianId);
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
