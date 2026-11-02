package com.safewalk.notification;

import com.safewalk.user.User;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 알림 발송 창구. 다른 도메인(보호자 연결, SOS, 경로 이탈 등)은 FCM을 직접 부르지 않고 이 서비스를 쓴다.
 *
 * {@link #notify}: 알림함 기록(notification 행) + 푸시
 *  - 기록은 호출한 쪽의 트랜잭션 안에서 저장된다 → 본 작업(예: 초대)이 실패하면 기록도 함께 취소된다.
 *  - 푸시는 그 트랜잭션이 커밋된 "뒤에" 보낸다 → 실패한 작업의 알림이 폰에 먼저 가는 일이 없다.
 *  - 푸시가 실패해도 예외를 던지지 않는다 → 알림 때문에 본 작업이 실패하지 않는다. (기록은 남으므로 알림함에서 볼 수 있다)
 *
 * 푸시 data에 실리는 값 (앱의 FirebaseMessagingService.onMessageReceived에서 사용)
 *  - type: 세부 이벤트 (예: GUARDIAN_INVITED, EMERGENCY_STARTED) — 화면 이동 판단용
 *  - category: notification.type (GUARDIAN / EMERGENCY / RISK_AREA)
 *  - notificationId, referenceType, referenceId
 *
 * 알림함 API: {@link #getMyNotifications}, {@link #countUnread}, {@link #markRead}, {@link #markAllRead}
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** 알림함 한 번에 가져오는 최대 개수 */
    public static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final PushDelivery pushDelivery;
    private final SmsSender smsSender;

    public NotificationService(NotificationRepository notificationRepository, PushDelivery pushDelivery,
                               SmsSender smsSender) {
        this.notificationRepository = notificationRepository;
        this.pushDelivery = pushDelivery;
        this.smsSender = smsSender;
    }

    // ── 발송 ───────────────────────────────────────────────────────────

    /**
     * 알림함에 기록하고, 커밋 후 받는 사람의 기기들로 푸시를 보낸다.
     *
     * @param recipient 받는 사람 (탈퇴한 계정이면 아무것도 하지 않는다)
     * @param category  알림함 분류 (notification.type)
     * @param event     세부 이벤트 이름 (푸시 data.type)
     * @param extraData 푸시 data에 추가로 실을 값 (선택)
     */
    @Transactional
    public void notify(User recipient, NotificationType category, String event, String title, String content,
                       ReferenceType referenceType, Long referenceId, Map<String, String> extraData) {
        if (recipient == null || recipient.getDeletedAt() != null) {
            return;
        }
        Notification saved = notificationRepository.save(
                new Notification(recipient, category, title, content, referenceType, referenceId));

        Map<String, String> data = new HashMap<>();
        if (extraData != null) {
            data.putAll(extraData);
        }
        data.put("type", event);
        data.put("category", category.name());
        data.put("notificationId", String.valueOf(saved.getNotificationId()));
        if (referenceType != null) {
            data.put("referenceType", referenceType.name());
            data.put("referenceId", String.valueOf(referenceId));
        }
        PushMessage message = new PushMessage(saved.getTitle(), content, data);
        Long recipientId = recipient.getUserId();

        afterCommit(() -> pushQuietly(recipientId, message));
    }

    /** 기록 없이 푸시만 보낸다 (개발용 테스트 푸시 등). 실패해도 예외를 던지지 않는다. */
    public PushResult pushToUser(Long userId, PushMessage message) {
        return pushDelivery.pushToUser(userId, message);
    }

    /** 현재 발송 방식 (FCM / CONSOLE) */
    public String pushMode() {
        return pushDelivery.mode();
    }

    /** 문자 발송. 진행 중인 트랜잭션이 있으면 커밋된 뒤에 보낸다 (실패한 작업의 문자가 나가지 않게) */
    public void sendSms(String phone, String text) {
        afterCommit(() -> smsSender.send(phone, text));
    }

    public void notifyRouteDeviation(Long userId) {
        // TODO: 경로 이탈 알림 발송 로직 구현
    }

    private void pushQuietly(Long userId, PushMessage message) {
        try {
            pushDelivery.pushToUser(userId, message);
        } catch (RuntimeException e) {
            log.error("푸시 발송 중 오류 (userId={}): {}", userId, e.getMessage(), e);
        }
    }

    /** 진행 중인 트랜잭션이 있으면 커밋 후에, 없으면 바로 실행한다. 롤백되면 실행하지 않는다. */
    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    // ── 알림함 ─────────────────────────────────────────────────────────

    /** GET /api/notifications?page=0&size=20 — 내 알림 (최신순) */
    @Transactional(readOnly = true)
    public NotificationResponses.Page getMyNotifications(Long userId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        Slice<Notification> slice = notificationRepository
                .findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(userId, PageRequest.of(page, size));
        List<NotificationResponses.Item> items = slice.getContent().stream()
                .map(NotificationResponses.Item::from)
                .toList();
        return new NotificationResponses.Page(items, page, size, slice.hasNext(),
                notificationRepository.countByUser_UserIdAndReadAtIsNull(userId));
    }

    /** GET /api/notifications/unread-count — 앱 아이콘·탭 배지용 */
    @Transactional(readOnly = true)
    public NotificationResponses.UnreadCount countUnread(Long userId) {
        return new NotificationResponses.UnreadCount(notificationRepository.countByUser_UserIdAndReadAtIsNull(userId));
    }

    /** PATCH /api/notifications/{id}/read — 다른 사람 알림이면 404. 이미 읽었으면 그대로 200 */
    @Transactional
    public NotificationResponses.Item markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByNotificationIdAndUser_UserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException("알림을 찾을 수 없습니다."));
        notification.markRead();
        return NotificationResponses.Item.from(notification);
    }

    /** PATCH /api/notifications/read-all — 안 읽은 알림 전체 읽음 처리 */
    @Transactional
    public NotificationResponses.ReadAll markAllRead(Long userId) {
        return new NotificationResponses.ReadAll(notificationRepository.markAllRead(userId, OffsetDateTime.now()));
    }
}
