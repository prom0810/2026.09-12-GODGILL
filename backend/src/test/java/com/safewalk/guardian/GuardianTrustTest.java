package com.safewalk.guardian;

import static org.assertj.core.api.Assertions.assertThat;

import com.safewalk.user.User;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GuardianTrustTest {

    private final User ward = user(1L, "010-1000-0000");

    private static User user(Long id, String phone) {
        User user = new User(null, "사용자" + id, "u" + id + "@example.com", "password", phone);
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    /** 이름·번호만 등록된 보호자 */
    private Guardian plain(long id) {
        Guardian guardian = new Guardian(ward, "보호자" + id, "010-2000-000" + id, Relationship.FAMILY, false);
        ReflectionTestUtils.setField(guardian, "guardianId", id);
        return guardian;
    }

    private Guardian verified(long id) {
        Guardian guardian = plain(id);
        guardian.markSmsVerified(OffsetDateTime.now());
        return guardian;
    }

    private Guardian connected(long id) {
        Guardian guardian = plain(id);
        guardian.invite();
        guardian.accept(user(100 + id, guardian.getPhone()));
        return guardian;
    }

    @Test
    void connectedOrVerifiedGuardianIsTrusted() {
        assertThat(GuardianTrust.isTrusted(plain(1))).isFalse();
        assertThat(GuardianTrust.isTrusted(verified(2))).isTrue();
        assertThat(GuardianTrust.isTrusted(connected(3))).isTrue();
    }

    @Test
    void deletingLastTrustedGuardianIsBlocked() {
        Guardian real = connected(1);
        Guardian fake = plain(2);

        assertThat(GuardianTrust.losesLastTrusted(List.of(real, fake), real, false)).isTrue();
    }

    @Test
    void deletingUntrustedGuardianIsAllowed() {
        Guardian real = connected(1);
        Guardian fake = plain(2);

        assertThat(GuardianTrust.losesLastTrusted(List.of(real, fake), fake, false)).isFalse();
    }

    @Test
    void deletingOneOfTwoTrustedGuardiansIsAllowed() {
        Guardian mom = connected(1);
        Guardian dad = verified(2);

        assertThat(GuardianTrust.losesLastTrusted(List.of(mom, dad), mom, false)).isFalse();
    }

    @Test
    void notBlockedWhenThereWasNoTrustedGuardian() {
        // 확인 기능 도입 전처럼 모두 미확인이면 이 규칙으로 막지 않는다 (최소 인원 규칙만 적용)
        Guardian a = plain(1);
        Guardian b = plain(2);

        assertThat(GuardianTrust.losesLastTrusted(List.of(a, b), a, false)).isFalse();
    }

    @Test
    void revokingConnectionOfVerifiedGuardianKeepsTrust() {
        Guardian both = connected(1);
        both.markSmsVerified(OffsetDateTime.now());

        // 연결을 끊어도 인증번호로 확인돼 있으면 문자를 받을 수 있어 믿을 수 있는 보호자로 남는다
        assertThat(GuardianTrust.losesLastTrusted(List.of(both), both, true)).isFalse();
        assertThat(GuardianTrust.losesLastTrusted(List.of(both), both, false)).isTrue();
    }

    @Test
    void successorPrefersVerifiedThenConnectedThenOldest() {
        Guardian oldPlain = plain(1);
        Guardian connected = connected(2);
        Guardian verified = verified(3);

        assertThat(GuardianTrust.pickPrimarySuccessor(List.of(oldPlain, connected, verified)))
                .containsSame(verified);
        assertThat(GuardianTrust.pickPrimarySuccessor(List.of(oldPlain, connected)))
                .containsSame(connected);
        assertThat(GuardianTrust.pickPrimarySuccessor(List.of(plain(5), plain(4))).orElseThrow().getGuardianId())
                .isEqualTo(4L);
        assertThat(GuardianTrust.pickPrimarySuccessor(List.of())).isEmpty();
    }
}
