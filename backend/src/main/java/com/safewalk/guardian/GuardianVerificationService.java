package com.safewalk.guardian;

import com.safewalk.notification.NotificationService;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 확인 (인증번호). 앱에 가입하지 않은 보호자가 "SOS 문자를 받는 것"에 동의했는지 확인한다.
 *
 * 흐름
 *  1. 사용자가 보호자에게 인증번호 보내기 → 보호자 번호로 6자리 문자 (MVP: 서버 로그 출력)
 *  2. 보호자가 사용자에게 번호를 알려 줌 (= 동의. 번호를 잘못 입력했다면 여기서 걸러진다)
 *  3. 사용자가 앱에 번호 입력 → guardian.sms_verified_at 기록
 *
 * SOS 문자는 확인된 보호자에게만 보낸다 ({@link com.safewalk.emergency.EmergencyService}).
 * 앱으로 연결된(CONNECTED) 보호자는 수락 자체가 동의라서 이 절차가 필요 없다 → 409.
 * 보호자가 앱에서 스스로 연결을 끊으면(leave) 확인도 함께 취소된다 ({@link GuardianConnectionService#leave}).
 *
 * 인증번호 규칙은 {@link GuardianVerificationCodeStore} 참고 (6자리, 3분, 1분 재발송 제한, 5회 실패 시 무효).
 * 다른 사람의 보호자에 요청하면 404.
 */
@Service
public class GuardianVerificationService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final GuardianRepository guardianRepository;
    private final UserService userService;
    private final GuardianVerificationCodeStore codeStore;
    private final NotificationService notificationService;
    private final boolean exposeCode;

    public GuardianVerificationService(
            GuardianRepository guardianRepository,
            UserService userService,
            GuardianVerificationCodeStore codeStore,
            NotificationService notificationService,
            @Value("${guardian.verification.expose-code:false}") boolean exposeCode
    ) {
        this.guardianRepository = guardianRepository;
        this.userService = userService;
        this.codeStore = codeStore;
        this.notificationService = notificationService;
        this.exposeCode = exposeCode;
    }

    /** POST /api/guardians/{guardianId}/verification — 보호자 번호로 인증번호 보내기 */
    @Transactional
    public GuardianVerificationResponses.Sent sendCode(Long userId, Long guardianId) {
        User user = userService.getActiveUserForUpdate(userId);
        Guardian guardian = findVerifiable(userId, guardianId);

        GuardianVerificationCodeStore.Issued issued = codeStore.issue(guardianId, Instant.now());
        // 문자는 트랜잭션이 커밋된 뒤에 나간다 (NotificationService.sendSms)
        notificationService.sendSms(guardian.getPhone(), message(user.getName(), issued.code()));

        return new GuardianVerificationResponses.Sent(
                guardianId,
                issued.expiresAt().atZone(KOREA).toOffsetDateTime(),
                issued.resendAvailableAt().atZone(KOREA).toOffsetDateTime(),
                exposeCode ? issued.code() : null);
    }

    /** POST /api/guardians/{guardianId}/verification/confirm — 보호자가 알려 준 인증번호 입력 */
    @Transactional
    public GuardianResponse confirm(Long userId, Long guardianId, GuardianVerificationResponses.ConfirmRequest request) {
        String code = request == null || request.code() == null ? "" : request.code().trim();
        if (!code.matches("\\d{" + GuardianVerificationCodeStore.CODE_LENGTH + "}")) {
            throw new IllegalArgumentException(
                    "인증번호 " + GuardianVerificationCodeStore.CODE_LENGTH + "자리 숫자를 입력해 주세요.");
        }
        userService.getActiveUserForUpdate(userId);
        Guardian guardian = findVerifiable(userId, guardianId);

        GuardianVerificationCodeStore.Check check = codeStore.verify(guardianId, code, Instant.now());
        switch (check.result()) {
            case OK -> guardian.markSmsVerified(OffsetDateTime.now());
            case MISMATCH -> throw new GuardianVerificationException(
                    "인증번호가 맞지 않습니다. " + check.remainingAttempts() + "번 더 입력할 수 있습니다.",
                    check.result(), check.remainingAttempts());
            case LOCKED -> throw new GuardianVerificationException(
                    "인증번호를 " + GuardianVerificationCodeStore.MAX_ATTEMPTS + "번 틀려 더 이상 사용할 수 없습니다. 인증번호를 다시 보내 주세요.",
                    check.result(), 0);
            case EXPIRED -> throw new GuardianVerificationException(
                    "인증번호 입력 시간이 지났습니다. 인증번호를 다시 보내 주세요.", check.result(), 0);
            case NOT_REQUESTED -> throw new GuardianVerificationException(
                    "보낸 인증번호가 없습니다. 인증번호를 먼저 보내 주세요.", check.result(), 0);
        }
        return GuardianResponse.from(guardian);
    }

    /** 내 보호자이고, 아직 확인 전이며, 앱으로 연결되지 않은 보호자만 */
    private Guardian findVerifiable(Long userId, Long guardianId) {
        Guardian guardian = guardianRepository
                .findByGuardianIdAndUser_UserIdAndDeletedAtIsNull(guardianId, userId)
                .orElseThrow(() -> new GuardianNotFoundException("보호자를 찾을 수 없습니다."));
        if (guardian.getRegistrationStatus() == RegistrationStatus.CONNECTED) {
            throw new GuardianStateException("앱으로 연결된 보호자는 인증번호 확인이 필요 없습니다.");
        }
        if (guardian.isSmsVerified()) {
            throw new GuardianStateException("이미 확인된 보호자입니다.");
        }
        return guardian;
    }

    static String message(String wardName, String code) {
        return "[안심동행] " + wardName + "님이 회원님을 보호자로 등록했어요. "
                + "SOS 알림 문자를 받는 데 동의하시면 인증번호 [" + code + "]를 " + wardName + "님께 알려 주세요. (3분 이내) "
                + "안심동행 앱을 설치해 이 번호로 가입하면 앱으로도 알림을 받을 수 있어요.";
    }
}
