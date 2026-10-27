package com.safewalk.guardian;

/**
 * 받은 보호자 연결 초대 (GET /api/guardians/invitations). 보호자 입장에서 본 값이다.
 *
 * - guardianId: 초대 ID. 수락·거절할 때 이 값을 쓴다
 * - wardName: 초대를 보낸 사용자(보호받는 사람)의 이름
 * - relationship / relationshipLabel: 그 사용자가 나를 등록한 관계 (예: PARENT / 부모)
 *
 * 수락 전에는 상대방 전화번호 등 다른 정보는 보여주지 않는다.
 */
public record GuardianInvitationResponse(
        Long guardianId,
        String wardName,
        String relationship,
        String relationshipLabel
) {

    public static GuardianInvitationResponse from(Guardian guardian) {
        return new GuardianInvitationResponse(
                guardian.getGuardianId(),
                guardian.getUser().getName(),
                guardian.getRelationship().name(),
                guardian.getRelationship().getLabel()
        );
    }
}
