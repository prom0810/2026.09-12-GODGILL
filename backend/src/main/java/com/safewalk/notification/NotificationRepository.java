package com.safewalk.notification;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 내 알림함 (최신순). 같은 시각이면 나중에 만든 것이 위로 오도록 ID로 한 번 더 정렬한다. */
    Slice<Notification> findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(Long userId, Pageable pageable);

    Optional<Notification> findByNotificationIdAndUser_UserId(Long notificationId, Long userId);

    long countByUser_UserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
