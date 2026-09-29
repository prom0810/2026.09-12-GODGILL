package com.safewalk.auth;

/**
 * 회원가입 요청. 현재는 이메일·비밀번호·사용자 유형(ADULT/MINOR)만 받는다.
 * 이름·전화번호는 아직 화면에 없으므로 요청 모델에도 포함하지 않는다.
 * 요청 JSON에서 필드가 빠지면 null 대신 빈 문자열로 채운다(기존 Kotlin 기본값과 동일한 동작).
 */
public record SignupRequest(String email, String password, String userType) {

    public SignupRequest {
        email = email == null ? "" : email;
        password = password == null ? "" : password;
        userType = userType == null ? "" : userType;
    }
}
