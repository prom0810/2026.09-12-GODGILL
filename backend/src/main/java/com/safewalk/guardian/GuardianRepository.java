package com.safewalk.guardian;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    List<Guardian> findByUser_UserIdAndDeletedAtIsNullOrderByGuardianIdAsc(Long userId);

    /** 본인 소유이면서 삭제되지 않은 보호자 1명 */
    Optional<Guardian> findByGuardianIdAndUser_UserIdAndDeletedAtIsNull(Long guardianId, Long userId);

    /** 삭제되지 않은 대표 보호자 (DB 제약상 최대 1명) */
    Optional<Guardian> findByUser_UserIdAndPrimaryTrueAndDeletedAtIsNull(Long userId);

    /** 삭제되지 않은 보호자 중 가장 먼저 등록된 1명 (대표 보호자 삭제 시 승계 대상) */
    Optional<Guardian> findFirstByUser_UserIdAndDeletedAtIsNullOrderByGuardianIdAsc(Long userId);

    long countByUser_UserIdAndDeletedAtIsNull(Long userId);

    /** 이 전화번호로 온 초대 중인 보호자 행 (보호자 입장의 "받은 초대" 목록) */
    List<Guardian> findByPhoneAndRegistrationStatusAndDeletedAtIsNullOrderByGuardianIdAsc(
            String phone, RegistrationStatus registrationStatus);

    /** 이 계정이 보호자로 연결된 행 (보호자 입장의 "내가 보호 중인 사용자" 목록) */
    List<Guardian> findByGuardianUser_UserIdAndRegistrationStatusAndDeletedAtIsNullOrderByConnectedAtAsc(
            Long guardianUserId, RegistrationStatus registrationStatus);

    /**
     * 삭제되지 않은 보호자 1명을 쓰기 잠금과 함께 조회한다 (SELECT ... FOR UPDATE).
     * 같은 초대에 수락·거절이 동시에 들어와도 차례로 처리되게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Guardian g where g.guardianId = :guardianId and g.deletedAt is null")
    Optional<Guardian> findActiveByIdForUpdate(@Param("guardianId") Long guardianId);

    boolean existsByUser_UserIdAndPhoneAndDeletedAtIsNull(Long userId, String phone);

    /** 이 사용자(ward)의 보호자 중 내 계정과 연결된(CONNECTED) 행. SOS 열람 권한 확인용 */
    Optional<Guardian> findFirstByUser_UserIdAndGuardianUser_UserIdAndRegistrationStatusAndDeletedAtIsNull(
            Long userId, Long guardianUserId, RegistrationStatus registrationStatus);
}
