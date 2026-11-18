package com.safewalk.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.safewalk.notification.NotificationService;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GuardianVerificationServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long GUARDIAN_ID = 31L;

    @Mock
    private GuardianRepository guardianRepository;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;

    private final GuardianVerificationCodeStore codeStore = new GuardianVerificationCodeStore();
    private User ward;
    private Guardian guardian;

    @BeforeEach
    void setUp() {
        ward = new User(null, "김민지", "ward@example.com", "password", "010-1111-2222");
        ReflectionTestUtils.setField(ward, "userId", USER_ID);
        guardian = new Guardian(ward, "엄마", "010-3333-4444", Relationship.PARENT, false);
        ReflectionTestUtils.setField(guardian, "guardianId", GUARDIAN_ID);
    }

    private GuardianVerificationService service(boolean exposeCode) {
        return new GuardianVerificationService(guardianRepository, userService, codeStore, notificationService, exposeCode);
    }

    private void guardianExists() {
        when(userService.getActiveUserForUpdate(USER_ID)).thenReturn(ward);
        when(guardianRepository.findByGuardianIdAndUser_UserIdAndDeletedAtIsNull(GUARDIAN_ID, USER_ID))
                .thenReturn(Optional.of(guardian));
    }

    @Test
    void sendCodeTextsGuardianAndHidesCodeByDefault() {
        guardianExists();

        GuardianVerificationResponses.Sent sent = service(false).sendCode(USER_ID, GUARDIAN_ID);

        assertThat(sent.guardianId()).isEqualTo(GUARDIAN_ID);
        assertThat(sent.devCode()).isNull();
        assertThat(sent.expiresAt()).isAfter(sent.resendAvailableAt());
        verify(notificationService).sendSms(eq("010-3333-4444"), contains("김민지님이 회원님을 보호자로 등록했어요"));
    }

    @Test
    void sendThenConfirmMarksGuardianVerified() {
        guardianExists();
        GuardianVerificationService service = service(true);

        String code = service.sendCode(USER_ID, GUARDIAN_ID).devCode();
        verify(notificationService).sendSms(eq("010-3333-4444"), contains("[" + code + "]"));

        GuardianResponse response = service.confirm(USER_ID, GUARDIAN_ID,
                new GuardianVerificationResponses.ConfirmRequest(" " + code + " "));

        assertThat(response.smsVerified()).isTrue();
        assertThat(response.smsVerifiedAt()).isNotNull();
        assertThat(guardian.isSmsVerified()).isTrue();
    }

    @Test
    void wrongCodeReportsRemainingAttempts() {
        guardianExists();
        GuardianVerificationService service = service(true);
        String code = service.sendCode(USER_ID, GUARDIAN_ID).devCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> service.confirm(USER_ID, GUARDIAN_ID,
                new GuardianVerificationResponses.ConfirmRequest(wrong)))
                .isInstanceOf(GuardianVerificationException.class)
                .satisfies(e -> {
                    GuardianVerificationException ex = (GuardianVerificationException) e;
                    assertThat(ex.getReason()).isEqualTo(GuardianVerificationCodeStore.Result.MISMATCH);
                    assertThat(ex.getRemainingAttempts()).isEqualTo(4);
                });
        assertThat(guardian.isSmsVerified()).isFalse();
    }

    @Test
    void confirmWithoutSendingIsRejected() {
        guardianExists();

        assertThatThrownBy(() -> service(false).confirm(USER_ID, GUARDIAN_ID,
                new GuardianVerificationResponses.ConfirmRequest("123456")))
                .isInstanceOf(GuardianVerificationException.class)
                .extracting(e -> ((GuardianVerificationException) e).getReason())
                .isEqualTo(GuardianVerificationCodeStore.Result.NOT_REQUESTED);
    }

    @Test
    void malformedCodeIsBadRequestBeforeAnyLookup() {
        assertThatThrownBy(() -> service(false).confirm(USER_ID, GUARDIAN_ID,
                new GuardianVerificationResponses.ConfirmRequest("12ab")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(false).confirm(USER_ID, GUARDIAN_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectedGuardianDoesNotNeedVerification() {
        User guardianAccount = new User(null, "엄마", "mom@example.com", "password", "010-3333-4444");
        ReflectionTestUtils.setField(guardianAccount, "userId", 8L);
        guardian.invite();
        guardian.accept(guardianAccount);
        guardianExists();

        assertThatThrownBy(() -> service(false).sendCode(USER_ID, GUARDIAN_ID))
                .isInstanceOf(GuardianStateException.class);
        verify(notificationService, never()).sendSms(anyString(), anyString());
    }

    @Test
    void alreadyVerifiedGuardianIsConflict() {
        guardian.markSmsVerified(java.time.OffsetDateTime.now());
        guardianExists();

        assertThatThrownBy(() -> service(false).sendCode(USER_ID, GUARDIAN_ID))
                .isInstanceOf(GuardianStateException.class);
    }

    @Test
    void otherUsersGuardianIsNotFound() {
        when(userService.getActiveUserForUpdate(USER_ID)).thenReturn(ward);
        when(guardianRepository.findByGuardianIdAndUser_UserIdAndDeletedAtIsNull(GUARDIAN_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(false).sendCode(USER_ID, GUARDIAN_ID))
                .isInstanceOf(GuardianNotFoundException.class);
    }

    @Test
    void secondSendWithinOneMinuteIsTooSoon() {
        guardianExists();
        GuardianVerificationService service = service(false);
        service.sendCode(USER_ID, GUARDIAN_ID);

        assertThatThrownBy(() -> service.sendCode(USER_ID, GUARDIAN_ID))
                .isInstanceOf(VerificationTooSoonException.class);
    }
}
