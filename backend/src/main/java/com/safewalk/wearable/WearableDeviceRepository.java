package com.safewalk.wearable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WearableDeviceRepository extends JpaRepository<WearableDevice, Long> {

    Optional<WearableDevice> findByUser_UserIdAndDeviceIdentifierAndDeletedAtIsNull(Long userId, String identifier);

    Optional<WearableDevice> findByWearableIdAndUser_UserIdAndDeletedAtIsNull(Long wearableId, Long userId);

    /** 내 연결 중인 기기 (정상이면 0~1개) */
    List<WearableDevice> findByUser_UserIdAndActiveTrueAndDeletedAtIsNull(Long userId);

    List<WearableDevice> findByUser_UserIdAndDeletedAtIsNullOrderByLastConnectedAtDesc(Long userId);

    /** 같은 워치를 다른 계정에서 연결 중인 행 (정상이면 0~1개, uq_wearable_identifier_connected) */
    Optional<WearableDevice> findFirstByDeviceIdentifierAndActiveTrueAndUser_UserIdNot(String identifier, Long userId);

    /**
     * 다른 계정의 연결이 만료됐으면 해제한다. 조건을 한 문장으로 다시 확인하므로 그 사이 상대가
     * 연결을 갱신했거나 경로 안내를 시작했으면 0을 돌려준다 (상대 사용자 행은 잠그지 않는다).
     *  - 연결 상태가 cutoff 이전에 마지막으로 바뀌었고
     *  - 그 계정이 경로 안내 중(IN_PROGRESS)이 아닐 때
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE wearable_device w
            SET is_active = FALSE, last_connected_at = :now
            WHERE w.wearable_id = :wearableId
              AND w.is_active
              AND COALESCE(w.last_connected_at, w.connected_at) < :cutoff
              AND NOT EXISTS (SELECT 1 FROM route r WHERE r.user_id = w.user_id AND r.status = 'IN_PROGRESS')
            """, nativeQuery = true)
    int releaseStaleConnection(@Param("wearableId") Long wearableId, @Param("cutoff") OffsetDateTime cutoff,
                               @Param("now") OffsetDateTime now);
}
