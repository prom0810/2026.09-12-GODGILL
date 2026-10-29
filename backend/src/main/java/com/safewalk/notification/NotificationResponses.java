package com.safewalk.notification;

import java.time.OffsetDateTime;
import java.util.List;

/** 알림함 API 응답 */
public final class NotificationResponses {

    private NotificationResponses() {
    }

    /** 알림 한 건 */
    public record Item(
            Long notificationId,
            NotificationType type,
            String title,
            String content,
            ReferenceType referenceType,
            Long referenceId,
            boolean read,
            OffsetDateTime readAt,
            OffsetDateTime createdAt) {

        public static Item from(Notification n) {
            return new Item(
                    n.getNotificationId(),
                    n.getType(),
                    n.getTitle(),
                    n.getContent(),
                    n.getReferenceType(),
                    n.getReferenceId(),
                    n.getReadAt() != null,
                    n.getReadAt(),
                    n.getCreatedAt());
        }
    }

    /** GET /api/notifications — hasNext가 true면 page+1로 다음 목록을 요청한다. */
    public record Page(List<Item> items, int page, int size, boolean hasNext, long unreadCount) {
    }

    /** GET /api/notifications/unread-count */
    public record UnreadCount(long unreadCount) {
    }

    /** PATCH /api/notifications/read-all */
    public record ReadAll(int updatedCount) {
    }
}
