package com.safewalk.user;

import com.safewalk.auth.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 조회.
 * 토큰은 유효해도 그 사이 탈퇴(deleted_at)했거나 삭제된 계정일 수 있으므로, 이 경우 401로 처리한다.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 탈퇴하지 않은 사용자를 반환한다. 없으면 401. */
    @Transactional(readOnly = true)
    public User getActiveUser(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않거나 탈퇴한 사용자입니다. 다시 로그인해 주세요."));
    }

    /**
     * 탈퇴하지 않은 사용자를 쓰기 잠금과 함께 반환한다. 없으면 401.
     * 호출하는 쪽의 트랜잭션이 끝날 때까지 같은 사용자에 대한 다른 잠금 요청은 대기한다.
     */
    @Transactional
    public User getActiveUserForUpdate(Long userId) {
        return userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않거나 탈퇴한 사용자입니다. 다시 로그인해 주세요."));
    }

    /** GET /api/users/me */
    @Transactional(readOnly = true)
    public UserResponse getMyInfo(Long userId) {
        return UserResponse.from(getActiveUser(userId));
    }
}
