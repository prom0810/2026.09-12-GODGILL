package com.safewalk.guardian;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 보호자 등록 요청. 회원가입(guardians 배열의 원소)과 POST /api/guardians에서 함께 사용한다.
 *
 * <pre>
 * { "name": "김엄마", "phone": "010-9876-5432", "relationship": "PARENT",
 *   "isPrimary": true, "canViewLocation": true }
 * </pre>
 *
 * - relationship: PARENT(부모) / FAMILY(가족) / ACQUAINTANCE(지인). 한글 값도 허용.
 * - isPrimary (선택): true면 이 보호자를 대표 보호자로 지정한다(기존 대표는 해제).
 *   생략하거나 false여도, 아직 대표 보호자가 없으면 자동으로 대표가 된다.
 * - canViewLocation (선택): 위치 열람 허용 여부. 생략 시 false.
 *
 * 요청 JSON에서 문자열 필드가 빠지면 null 대신 빈 문자열, 선택 필드가 빠지면 false로 채운다.
 */
public record GuardianRequest(
        String name,
        String phone,
        String relationship,
        @JsonProperty("isPrimary") Boolean isPrimary,
        Boolean canViewLocation
) {

    public GuardianRequest {
        name = name == null ? "" : name;
        phone = phone == null ? "" : phone;
        relationship = relationship == null ? "" : relationship;
        isPrimary = isPrimary != null && isPrimary;
        canViewLocation = canViewLocation != null && canViewLocation;
    }

    /** 기존 코드 호환용: 선택 필드 없이 생성 */
    public GuardianRequest(String name, String phone, String relationship) {
        this(name, phone, relationship, false, false);
    }
}
