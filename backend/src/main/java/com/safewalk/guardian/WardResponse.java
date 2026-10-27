package com.safewalk.guardian;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * 내가 보호 중인 사용자 (GET /api/guardians/wards). 보호자 입장에서 본 값이다.
 *
 * - guardianId: 연결 ID. 연결을 끊을 때(POST /api/guardians/wards/{guardianId}/leave) 이 값을 쓴다
 * - isPrimary: 내가 이 사용자의 대표 보호자인지
 * - canViewLocation: 이 사용자가 나에게 위치 열람을 허용했는지 (사용자 본인만 바꿀 수 있다)
 */
public record WardResponse(
        Long guardianId,
        String wardName,
        String wardPhone,
        String relationship,
        String relationshipLabel,
        @JsonProperty("isPrimary") boolean isPrimary,
        boolean canViewLocation,
        OffsetDateTime connectedAt
) {

    public static WardResponse from(Guardian guardian) {
        return new WardResponse(
                guardian.getGuardianId(),
                guardian.getUser().getName(),
                guardian.getUser().getPhone(),
                guardian.getRelationship().name(),
                guardian.getRelationship().getLabel(),
                guardian.isPrimary(),
                guardian.isCanViewLocation(),
                guardian.getConnectedAt()
        );
    }
}
