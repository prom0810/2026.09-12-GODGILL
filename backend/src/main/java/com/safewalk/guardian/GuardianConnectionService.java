package com.safewalk.guardian;

import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 계정 연결 (초대 → 수락 → 해제).
 *
 * 등장인물
 *  - 사용자(ward): 보호받는 사람. 보호자를 등록하고 초대를 보낸다
 *  - 보호자 계정: 앱에 가입한 보호자. 자기 전화번호로 온 초대를 수락/거절한다
 *
 * 초대 매칭 규칙
 *  - 보호자 행의 phone과 같은 전화번호로 가입한 계정이 초대를 받는다 (users.phone은 중복 불가)
 *  - 초대를 보낼 때 그 번호로 가입한 계정이 없어도 된다. 나중에 그 번호로 가입하면 초대가 보인다
 *    (가입 여부를 초대하는 쪽에 드러내지 않기 위해서이기도 하다)
 *
 * ⚠ 보안 주의 (MVP 한계)
 *  - 지금은 가입 시 전화번호 본인 인증이 없다. 다른 사람 번호로 가입하면 그 사람에게 온 초대를 수락할 수 있다.
 *    본인 인증(users.identity_verified) 도입 시 accept()에서 인증된 계정만 허용하도록 바꿔야 한다.
 *
 * 상태 전이는 {@link Guardian}의 invite/cancelInvite/accept/reject/revoke 참고. 맞지 않는 상태면 409.
 * 다른 사람의 보호자·초대에 접근하면 존재 여부를 드러내지 않도록 404로 응답한다.
 */
@Service
public class GuardianConnectionService {

    private final GuardianRepository guardianRepository;
    private final UserService userService;

    public GuardianConnectionService(GuardianRepository guardianRepository, UserService userService) {
        this.guardianRepository = guardianRepository;
        this.userService = userService;
    }

    // ── 사용자(ward) 쪽 ───────────────────────────────────────────────

    /** POST /api/guardians/{guardianId}/invite — 내 보호자에게 연결 초대 보내기 */
    @Transactional
    public GuardianResponse invite(Long userId, Long guardianId) {
        userService.getActiveUserForUpdate(userId);
        Guardian guardian = findOwned(userId, guardianId);
        guardian.invite();
        return GuardianResponse.from(guardian);
    }

    /**
     * POST /api/guardians/{guardianId}/revoke — 초대 취소 또는 연결 해제.
     * 초대 중이면 UNREGISTERED로, 연결돼 있으면 REVOKED로 바꾼다. 보호자 행 자체는 남는다(삭제는 DELETE).
     */
    @Transactional
    public GuardianResponse revoke(Long userId, Long guardianId) {
        userService.getActiveUserForUpdate(userId);
        Guardian guardian = findOwned(userId, guardianId);
        if (guardian.getRegistrationStatus() == RegistrationStatus.INVITED) {
            guardian.cancelInvite();
        } else {
            guardian.revoke();
        }
        return GuardianResponse.from(guardian);
    }

    // ── 보호자 계정 쪽 ─────────────────────────────────────────────────

    /** GET /api/guardians/invitations — 내 전화번호로 온 연결 초대 목록 */
    @Transactional(readOnly = true)
    public List<GuardianInvitationResponse> getInvitations(Long accountId) {
        User me = userService.getActiveUser(accountId);
        return guardianRepository
                .findByPhoneAndRegistrationStatusAndDeletedAtIsNullOrderByGuardianIdAsc(
                        me.getPhone(), RegistrationStatus.INVITED)
                .stream()
                .filter(g -> isActiveWardOtherThan(g, me))
                .map(GuardianInvitationResponse::from)
                .toList();
    }

    /** POST /api/guardians/invitations/{guardianId}/accept — 초대 수락 */
    @Transactional
    public WardResponse accept(Long accountId, Long guardianId) {
        User me = userService.getActiveUser(accountId);
        Guardian guardian = findInvitationForMe(guardianId, me);
        guardian.accept(me);
        return WardResponse.from(guardian);
    }

    /** POST /api/guardians/invitations/{guardianId}/reject — 초대 거절 */
    @Transactional
    public void reject(Long accountId, Long guardianId) {
        User me = userService.getActiveUser(accountId);
        Guardian guardian = findInvitationForMe(guardianId, me);
        guardian.reject();
    }

    /** GET /api/guardians/wards — 내가 보호자로 연결된 사용자 목록 (연결 순) */
    @Transactional(readOnly = true)
    public List<WardResponse> getWards(Long accountId) {
        User me = userService.getActiveUser(accountId);
        return guardianRepository
                .findByGuardianUser_UserIdAndRegistrationStatusAndDeletedAtIsNullOrderByConnectedAtAsc(
                        me.getUserId(), RegistrationStatus.CONNECTED)
                .stream()
                .filter(g -> isActiveWardOtherThan(g, me))
                .map(WardResponse::from)
                .toList();
    }

    /** POST /api/guardians/wards/{guardianId}/leave — 보호자가 스스로 연결 끊기 */
    @Transactional
    public void leave(Long accountId, Long guardianId) {
        User me = userService.getActiveUser(accountId);
        Guardian guardian = guardianRepository.findActiveByIdForUpdate(guardianId)
                .filter(g -> g.getRegistrationStatus() == RegistrationStatus.CONNECTED)
                .filter(g -> g.getGuardianUser() != null && g.getGuardianUser().getUserId().equals(me.getUserId()))
                .orElseThrow(() -> new GuardianNotFoundException("연결된 사용자를 찾을 수 없습니다."));
        guardian.revoke();
    }

    // ── 내부 ────────────────────────────────────────────────────────────

    private Guardian findOwned(Long userId, Long guardianId) {
        return guardianRepository
                .findByGuardianIdAndUser_UserIdAndDeletedAtIsNull(guardianId, userId)
                .orElseThrow(() -> new GuardianNotFoundException("보호자를 찾을 수 없습니다."));
    }

    /**
     * 내 전화번호로 등록된 보호자 행을 잠금과 함께 가져온다. 내 번호가 아니면 404.
     * 상태(INVITED인지)는 여기서 보지 않고 accept()/reject()가 검사한다 → 이미 수락한 초대 등은 409.
     */
    private Guardian findInvitationForMe(Long guardianId, User me) {
        return guardianRepository.findActiveByIdForUpdate(guardianId)
                .filter(g -> g.getPhone().equals(me.getPhone()))
                .filter(g -> isActiveWardOtherThan(g, me))
                .orElseThrow(() -> new GuardianNotFoundException("초대를 찾을 수 없습니다."));
    }

    /** 초대한 사용자가 탈퇴하지 않았고, 나 자신이 아닌지 */
    private boolean isActiveWardOtherThan(Guardian guardian, User me) {
        User ward = guardian.getUser();
        return ward.getDeletedAt() == null && !ward.getUserId().equals(me.getUserId());
    }
}
