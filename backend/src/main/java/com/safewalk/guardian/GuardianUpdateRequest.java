package com.safewalk.guardian;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 보호자 정보 수정 요청 (PATCH /api/guardians/{guardianId}). 보낸 필드만 바뀌고, 생략(null)한 필드는 그대로 둔다.
 *
 * <pre>
 * { "name": "엄마", "relationship": "PARENT", "isPrimary": true, "canViewLocation": false }
 * </pre>
 *
 * - isPrimary: true면 이 보호자를 대표로 지정한다(기존 대표는 자동 해제).
 *   대표 보호자에게 false를 보내면 400 — 대표는 항상 1명 있어야 하므로 다른 보호자를 대표로 지정해야 한다.
 * - phone: 수정할 수 없다(보내면 400). 보호자 계정 연결의 기준이 되는 값이라, 번호가 바뀌면 삭제 후 다시 등록한다.
 */
public record GuardianUpdateRequest(
        String name,
        String phone,
        String relationship,
        @JsonProperty("isPrimary") Boolean isPrimary,
        Boolean canViewLocation
) {
}
