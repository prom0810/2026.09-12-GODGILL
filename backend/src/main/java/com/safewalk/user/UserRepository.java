package com.safewalk.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * 탈퇴하지 않은 사용자를 조회하면서 해당 행에 쓰기 잠금(SELECT ... FOR UPDATE)을 건다.
     * 같은 사용자의 보호자 추가·삭제 요청이 동시에 들어와도 차례로 처리되게 해서
     * "최대 5명", "미성년자 최소 1명" 규칙이 깨지지 않도록 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId and u.deletedAt is null")
    Optional<User> findActiveByIdForUpdate(@Param("userId") Long userId);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);
}
