package com.safewalk.device;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByFcmToken(String fcmToken);

    /** 이 사용자에게 푸시를 보낼 기기들 */
    List<UserDevice> findByUser_UserIdAndActiveTrueOrderByUserDeviceIdAsc(Long userId);

    /** FCM이 "더 이상 쓸 수 없는 토큰"이라고 알려 준 기기를 한 번에 비활성화한다. */
    @Modifying
    @Query("update UserDevice d set d.active = false, d.updatedAt = :now "
            + "where d.fcmToken in :tokens and d.active = true")
    int deactivateByTokens(@Param("tokens") Collection<String> tokens, @Param("now") OffsetDateTime now);
}
