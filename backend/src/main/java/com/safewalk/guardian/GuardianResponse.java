package com.safewalk.guardian;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * 보호자 응답.
 *
 * - relationship은 코드(PARENT 등), relationshipLabel은 화면 표시용 한글(부모 등)
 * - registrationStatus: UNREGISTERED / INVITED / CONNECTED / REVOKED ({@link RegistrationStatus})
 * - connectedAt: 보호자 앱 계정과 연결된 시각. 연결 전에는 null
 * - smsVerified: 인증번호로 SOS 문자 수신에 동의했는지. 앱 없는 보호자는 true여야 SOS 문자를 받는다
 *   (false면 앱에서 "확인되지 않은 보호자"로 표시). smsVerifiedAt: 동의한 시각
 *
 * 보호자의 앱 계정 ID(guardian_user_id)는 내부 값이라 응답에 넣지 않는다.
 */
public record GuardianResponse(
        Long guardianId,
        String name,
        String phone,
        String relationship,
        String relationshipLabel,
        @JsonProperty("isPrimary") boolean isPrimary,
        String registrationStatus,
        boolean canViewLocation,
        OffsetDateTime createdAt,
        OffsetDateTime connectedAt,
        boolean smsVerified,
        OffsetDateTime smsVerifiedAt
) {

    public static GuardianResponse from(Guardian guardian) {
        return new GuardianResponse(
                guardian.getGuardianId(),
                guardian.getName(),
                guardian.getPhone(),
                guardian.getRelationship().name(),
                guardian.getRelationship().getLabel(),
                guardian.isPrimary(),
                guardian.getRegistrationStatus().name(),
                guardian.isCanViewLocation(),
                guardian.getCreatedAt(),
                guardian.getConnectedAt(),
                guardian.isSmsVerified(),
                guardian.getSmsVerifiedAt()
        );
    }
}
