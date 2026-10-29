package com.safewalk.guardian;

/**
 * 보호자의 앱 계정 연결 상태 (guardian.registration_status).
 *
 * - UNREGISTERED: 이름·전화번호만 등록된 상태. 앱 계정과 연결되지 않음 (기본값)
 * - INVITED: 연결 초대를 보낸 상태
 * - CONNECTED: 보호자가 앱 계정으로 수락해 연결된 상태.
 *              guardian_user_id·connected_at이 반드시 있어야 한다 (DB 제약 ck_guardian_connected)
 * - REVOKED: 연결이 해제된 상태
 *
 * 상태를 바꾸는 API는 {@link GuardianConnectionController}(초대·수락·거절·해제), 전이 규칙은 {@link Guardian} 참고.
 * 보호자를 삭제(DELETE)할 때 INVITED/CONNECTED였다면 REVOKED로 바뀐다.
 */
public enum RegistrationStatus {
    UNREGISTERED,
    INVITED,
    CONNECTED,
    REVOKED
}
