package com.safewalk.user;

import java.time.OffsetDateTime;

/** 내 정보 응답. 비밀번호(해시 포함)는 절대 포함하지 않는다. */
public record UserResponse(
        Long userId,
        String name,
        String email,
        String phone,
        String userType,
        OffsetDateTime createdAt
) {

    /** user.getUserType()이 LAZY 로딩이므로 트랜잭션 안에서 호출해야 한다. */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getUserType().getTypeName(),
                user.getCreatedAt()
        );
    }
}
