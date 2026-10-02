package com.safewalk.auth;

import com.safewalk.guardian.GuardianRequest;
import java.util.List;

/**
 * 회원가입 요청.
 *
 * <pre>
 * {
 *   "email": "kid@test.com", "password": "1234",
 *   "name": "김안심", "phone": "010-1234-5678", "userType": "MINOR",
 *   "guardians": [ { "name": "김엄마", "phone": "01098765432", "relationship": "PARENT" } ]
 * }
 * </pre>
 *
 * - guardians: MINOR는 1명 이상 필수, ADULT는 생략 가능(보내면 함께 저장). 최대 5명.
 * - 요청 JSON에서 문자열 필드가 빠지면 null 대신 빈 문자열, guardians가 빠지면 빈 목록으로 채운다.
 */
public record SignupRequest(
        String email,
        String password,
        String name,
        String phone,
        String userType,
        List<GuardianRequest> guardians
) {

    public SignupRequest {
        email = email == null ? "" : email;
        password = password == null ? "" : password;
        name = name == null ? "" : name;
        phone = phone == null ? "" : phone;
        userType = userType == null ? "" : userType;
        guardians = guardians == null ? List.of() : guardians;
    }
}
