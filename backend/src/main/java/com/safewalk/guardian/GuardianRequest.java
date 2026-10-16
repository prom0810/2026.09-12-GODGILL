package com.safewalk.guardian;

/**
 * 보호자 등록 요청. 회원가입(guardians 배열의 원소)과 POST /api/guardians에서 함께 사용한다.
 *
 * <pre>
 * { "name": "김엄마", "phone": "010-9876-5432", "relationship": "PARENT" }
 * </pre>
 *
 * relationship: PARENT(부모) / FAMILY(가족) / ACQUAINTANCE(지인). 한글 값도 허용.
 * 요청 JSON에서 필드가 빠지면 null 대신 빈 문자열로 채운다.
 */
public record GuardianRequest(String name, String phone, String relationship) {

    public GuardianRequest {
        name = name == null ? "" : name;
        phone = phone == null ? "" : phone;
        relationship = relationship == null ? "" : relationship;
    }
}
