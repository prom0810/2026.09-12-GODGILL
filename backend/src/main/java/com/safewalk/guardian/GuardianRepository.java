package com.safewalk.guardian;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    List<Guardian> findByUser_UserIdAndDeletedAtIsNullOrderByGuardianIdAsc(Long userId);

    /** 본인 소유이면서 삭제되지 않은 보호자 1명 */
    Optional<Guardian> findByGuardianIdAndUser_UserIdAndDeletedAtIsNull(Long guardianId, Long userId);

    /** 삭제되지 않은 대표 보호자 (DB 제약상 최대 1명) */
    Optional<Guardian> findByUser_UserIdAndPrimaryTrueAndDeletedAtIsNull(Long userId);

    /** 삭제되지 않은 보호자 중 가장 먼저 등록된 1명 (대표 보호자 삭제 시 승계 대상) */
    Optional<Guardian> findFirstByUser_UserIdAndDeletedAtIsNullOrderByGuardianIdAsc(Long userId);

    long countByUser_UserIdAndDeletedAtIsNull(Long userId);

    boolean existsByUser_UserIdAndPhoneAndDeletedAtIsNull(Long userId, String phone);
}
