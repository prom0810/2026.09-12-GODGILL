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
 * 초대·수락·해제 API는 아직 없다. 지금은 보호자 추가 시 UNREGISTERED로 저장하고,
 * 삭제 시 INVITED/CONNECTED 상태였다면 REVOKED로 바꾸는 것만 한다.
 */
public enum RegistrationStatus {
    UNREGISTERED,
    INVITED,
    CONNECTED,
    REVOKED
}
