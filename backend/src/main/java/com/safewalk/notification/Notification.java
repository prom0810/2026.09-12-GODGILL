package com.safewalk.notification;

import com.safewalk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 앱 알림함에 남는 알림 한 건 (DB v3 notification 테이블, users와 N:1).
 *
 * 푸시는 한 번 오고 사라지지만 이 기록은 남아서, 폰이 꺼져 있었거나 알림을 지운 경우에도
 * 알림함(GET /api/notifications)에서 다시 볼 수 있다.
 *
 * - referenceType / referenceId: 알림이 가리키는 대상. 둘 다 있거나 둘 다 없어야 한다 (DB ck_notification_reference_pair)
 * - readAt: null이면 안 읽음
 */
@Entity
@Table(name = "notification")
public class Notification {

    /** DB 컬럼 길이 (title VARCHAR(100)) */
    public static final int MAX_TITLE_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 30)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = now();

    /** JPA 전용 기본 생성자 */
    protected Notification() {
    }

    public Notification(User user, NotificationType type, String title, String content,
                        ReferenceType referenceType, Long referenceId) {
        if ((referenceType == null) != (referenceId == null)) {
            throw new IllegalArgumentException("referenceType과 referenceId는 함께 있어야 합니다.");
        }
        this.user = user;
        this.type = type;
        this.title = title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
        this.content = content;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = now();
    }

    /** 읽음 처리. 이미 읽은 알림은 처음 읽은 시각을 유지한다. */
    public void markRead() {
        if (readAt == null) {
            readAt = now();
        }
    }

    /**
     * DB(timestamptz)에 저장되는 형태와 같은 값: UTC, 마이크로초 단위.
     * 그냥 OffsetDateTime.now()를 쓰면 저장 직후 응답(서버 시간대, 더 세밀한 단위)과
     * 다시 조회한 응답(UTC, 마이크로초)이 같은 시각인데도 다르게 표시된다.
     */
    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getUserId().equals(userId);
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public User getUser() {
        return user;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
