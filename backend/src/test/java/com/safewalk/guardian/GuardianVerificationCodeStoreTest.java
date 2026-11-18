package com.safewalk.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.safewalk.guardian.GuardianVerificationCodeStore.Check;
import com.safewalk.guardian.GuardianVerificationCodeStore.Issued;
import com.safewalk.guardian.GuardianVerificationCodeStore.Result;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GuardianVerificationCodeStoreTest {

    private static final Instant T0 = Instant.parse("2026-11-17T11:00:00Z");

    private final GuardianVerificationCodeStore store = new GuardianVerificationCodeStore();

    @Test
    void issuesSixDigitCodeValidForThreeMinutes() {
        Issued issued = store.issue(1L, T0);

        assertThat(issued.code()).matches("\\d{6}");
        assertThat(issued.expiresAt()).isEqualTo(T0.plusSeconds(180));
        assertThat(issued.resendAvailableAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void correctCodeIsAcceptedOnlyOnce() {
        Issued issued = store.issue(1L, T0);

        assertThat(store.verify(1L, issued.code(), T0.plusSeconds(10)).result()).isEqualTo(Result.OK);
        assertThat(store.verify(1L, issued.code(), T0.plusSeconds(11)).result()).isEqualTo(Result.NOT_REQUESTED);
    }

    @Test
    void notRequestedWhenNoCodeWasIssued() {
        assertThat(store.verify(1L, "123456", T0).result()).isEqualTo(Result.NOT_REQUESTED);
    }

    @Test
    void codesAreKeptPerGuardian() {
        Issued first = store.issue(1L, T0);
        Issued second = store.issue(2L, T0); // 다른 보호자는 1분 제한과 상관없다

        assertThat(store.verify(1L, first.code(), T0).result()).isEqualTo(Result.OK);
        assertThat(store.verify(2L, second.code(), T0).result()).isEqualTo(Result.OK);
    }

    @Test
    void resendWithinOneMinuteIsRejected() {
        store.issue(1L, T0);

        assertThatThrownBy(() -> store.issue(1L, T0.plusSeconds(20)))
                .isInstanceOf(VerificationTooSoonException.class)
                .extracting(e -> ((VerificationTooSoonException) e).getRetryAfterSeconds())
                .isEqualTo(40L);
    }

    @Test
    void resendAfterOneMinuteReplacesPreviousCode() {
        Issued first = store.issue(1L, T0);
        Issued second = store.issue(1L, T0.plusSeconds(60));

        if (!first.code().equals(second.code())) {
            assertThat(store.verify(1L, first.code(), T0.plusSeconds(61)).result()).isEqualTo(Result.MISMATCH);
        }
        assertThat(store.verify(1L, second.code(), T0.plusSeconds(62)).result()).isEqualTo(Result.OK);
    }

    @Test
    void expiredAfterThreeMinutes() {
        Issued issued = store.issue(1L, T0);

        assertThat(store.verify(1L, issued.code(), T0.plusSeconds(180)).result()).isEqualTo(Result.EXPIRED);
    }

    @Test
    void wrongCodeCountsDownAndLocksAfterFiveFailures() {
        Issued issued = store.issue(1L, T0);
        String wrong = issued.code().equals("000000") ? "111111" : "000000";

        for (int remaining = 4; remaining >= 1; remaining--) {
            Check check = store.verify(1L, wrong, T0);
            assertThat(check.result()).isEqualTo(Result.MISMATCH);
            assertThat(check.remainingAttempts()).isEqualTo(remaining);
        }
        assertThat(store.verify(1L, wrong, T0).result()).isEqualTo(Result.LOCKED);
        // 잠긴 뒤에는 맞는 번호도 받지 않는다
        assertThat(store.verify(1L, issued.code(), T0).result()).isEqualTo(Result.LOCKED);
        // 다시 발급받으면 새 번호로 확인할 수 있다
        Issued again = store.issue(1L, T0.plusSeconds(60));
        assertThat(store.verify(1L, again.code(), T0.plusSeconds(61)).result()).isEqualTo(Result.OK);
    }
}
