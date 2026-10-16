package com.safewalk.guardian;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    List<Guardian> findByUser_UserIdAndDeletedAtIsNullOrderByGuardianIdAsc(Long userId);

    /** 본인 소유이면서 삭제되지 않은 보호자 1명 */
    Optional<Guardian> findByGuardianIdAndUser_UserIdAndDeletedAtIsNull(Long guardianId, Long userId);

    long countByUser_UserIdAndDeletedAtIsNull(Long userId);

    boolean existsByUser_UserIdAndPhoneAndDeletedAtIsNull(Long userId, String phone);
}
