package com.safewalk.guardian;

import com.safewalk.notification.NotificationService;
import com.safewalk.notification.NotificationType;
import com.safewalk.notification.ReferenceType;
import com.safewalk.user.User;
import com.safewalk.user.UserRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 보호자 연결 상태가 바뀔 때 상대방에게 보내는 알림 (알림함 기록 + 푸시).
 *
 * | 이벤트                | 받는 사람                        | 언제                                   |
 * |-----------------------|----------------------------------|----------------------------------------|
 * | GUARDIAN_INVITED      | 그 전화번호로 가입한 보호자 계정 | 사용자가 초대를 보냄 (계정이 없으면 생략) |
 * | GUARDIAN_ACCEPTED     | 사용자(보호받는 사람)            | 보호자가 초대를 수락함                   |
 * | GUARDIAN_DISCONNECTED | 보호자 계정                      | 사용자가 연결을 해제하거나 보호자를 삭제함 |
 * | GUARDIAN_LEFT         | 사용자                           | 보호자가 스스로 연결을 끊음              |
 *
 * 초대 거절, 보낸 초대 취소는 알리지 않는다 (상대에게 부담을 주지 않기 위함. 상태는 목록에서 확인 가능).
 * 모든 알림은 reference_type=GUARDIAN, reference_id=guardian_id 이다.
 * 호출한 쪽의 트랜잭션이 커밋된 뒤에 푸시가 나간다 ({@link NotificationService#notify}).
 */
@Component
public class GuardianNotifier {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public GuardianNotifier(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /** 사용자 → 보호자: 연결 요청. 그 번호로 가입한 계정(본인 제외)이 있을 때만 보낸다. */
    public void invited(Guardian guardian) {
        User ward = guardian.getUser();
        userRepository.findByPhoneAndDeletedAtIsNull(guardian.getPhone())
                .filter(account -> !account.getUserId().equals(ward.getUserId()))
                .ifPresent(account -> send(account, guardian, "GUARDIAN_INVITED",
                        "보호자 연결 요청",
                        ward.getName() + "님이 회원님을 보호자(" + guardian.getRelationship().getLabel()
                                + ")로 등록하고 연결을 요청했어요."));
    }

    /** 보호자 → 사용자: 초대 수락 */
    public void accepted(Guardian guardian) {
        send(guardian.getUser(), guardian, "GUARDIAN_ACCEPTED",
                "보호자 연결 완료",
                guardian.getName() + "님이 보호자 연결을 수락했어요.");
    }

    /** 사용자 → 보호자: 사용자가 연결을 해제했거나 보호자 목록에서 삭제함. 연결돼 있던 계정에만 보낸다. */
    public void disconnectedByWard(Guardian guardian) {
        User account = guardian.getGuardianUser();
        if (account == null) {
            return;
        }
        send(account, guardian, "GUARDIAN_DISCONNECTED",
                "보호자 연결 해제",
                guardian.getUser().getName() + "님과의 보호자 연결이 해제되었어요.");
    }

    /** 보호자 → 사용자: 보호자가 스스로 연결을 끊음 */
    public void leftByGuardian(Guardian guardian) {
        send(guardian.getUser(), guardian, "GUARDIAN_LEFT",
                "보호자 연결 해제",
                guardian.getName() + "님이 보호자 연결을 해제했어요.");
    }

    private void send(User recipient, Guardian guardian, String event, String title, String content) {
        notificationService.notify(recipient, NotificationType.GUARDIAN, event, title, content,
                ReferenceType.GUARDIAN, guardian.getGuardianId(),
                Map.of("guardianId", String.valueOf(guardian.getGuardianId())));
    }
}
