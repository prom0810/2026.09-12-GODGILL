package com.safewalk.guardian;

import java.time.OffsetDateTime;

/**
 * 보호자 응답. relationship은 코드(PARENT 등), relationshipLabel은 화면 표시용 한글(부모 등).
 */
public record GuardianResponse(
        Long guardianId,
        String name,
        String phone,
        String relationship,
        String relationshipLabel,
        OffsetDateTime createdAt
) {

    public static GuardianResponse from(Guardian guardian) {
        return new GuardianResponse(
                guardian.getGuardianId(),
                guardian.getName(),
                guardian.getPhone(),
                guardian.getRelationship().name(),
                guardian.getRelationship().getLabel(),
                guardian.getCreatedAt()
        );
    }
}
