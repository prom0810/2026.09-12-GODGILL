package com.safewalk.emergency;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmergencyEventRepository extends JpaRepository<EmergencyEvent, Long> {

    /** 진행 중인 사건 상태 목록 */
    List<EmergencyStatus> OPEN_STATUSES = List.of(EmergencyStatus.OCCURRED, EmergencyStatus.IN_PROGRESS);

    /** 사용자의 진행 중인 사건 (사용자당 최대 1건, DB uq_emergency_one_open) */
    Optional<EmergencyEvent> findFirstByUser_UserIdAndStatusInOrderByOccurredAtDesc(
            Long userId, Collection<EmergencyStatus> statuses);

    /** 여러 사용자의 진행 중인 사건 (보호자가 보호 중인 사람들의 SOS 목록) */
    List<EmergencyEvent> findByUser_UserIdInAndStatusInOrderByOccurredAtDesc(
            Collection<Long> userIds, Collection<EmergencyStatus> statuses);

    /** 종료·상태 변경용: 같은 사건에 동시에 종료 요청이 와도 하나씩 처리되게 잠금 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmergencyEvent e where e.emergencyId = :id")
    Optional<EmergencyEvent> findByIdForUpdate(@Param("id") Long id);

    /** 자동 종료 대상: 진행 중이면서 이 시각보다 먼저 발생한 사건 */
    List<EmergencyEvent> findByStatusInAndOccurredAtBefore(Collection<EmergencyStatus> statuses,
                                                           OffsetDateTime before);

    /** 진행 중인 사건 전체 (서버 재시작 시 위치 수신 기준 시각 초기화 등) */
    List<EmergencyEvent> findByStatusIn(Collection<EmergencyStatus> statuses);
}
