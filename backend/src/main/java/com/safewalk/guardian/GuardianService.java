package com.safewalk.guardian;

import com.safewalk.global.PhoneNumbers;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 추가·조회·삭제.
 *
 * 추가 규칙 (회원가입 시 등록과 POST /api/guardians 공통):
 *  - name·phone·relationship 필수, relationship은 PARENT/FAMILY/ACQUAINTANCE (한글 부모/가족/지인도 허용)
 *  - 전화번호는 "010-1234-5678" 형태로 정규화
 *  - 본인 전화번호는 보호자로 등록 불가 (400)
 *  - 같은 사용자에게 같은 전화번호 중복 등록 불가 (409). 삭제된 보호자의 번호는 다시 등록할 수 있다.
 *  - 사용자당 최대 {@link #MAX_GUARDIANS}명 (400)
 *
 * 삭제 규칙 (DELETE /api/guardians/{guardianId}):
 *  - soft delete (deleted_at 기록)
 *  - 본인 보호자가 아니거나, 없거나, 이미 삭제된 경우 404
 *  - 미성년자(MINOR)는 마지막 남은 보호자 1명을 삭제할 수 없다 (400)
 *
 * 추가·삭제는 사용자 행에 쓰기 잠금을 걸고 처리하므로, 동시에 요청이 들어와도 위 개수 규칙이 유지된다.
 */
@Service
public class GuardianService {

    public static final int MAX_GUARDIANS = 5;
    private static final int MIN_GUARDIANS_FOR_MINOR = 1;
    private static final int NAME_MAX_LENGTH = 50;

    private final GuardianRepository guardianRepository;
    private final UserService userService;

    public GuardianService(GuardianRepository guardianRepository, UserService userService) {
        this.guardianRepository = guardianRepository;
        this.userService = userService;
    }

    /** POST /api/guardians — 로그인한 사용자에게 보호자 1명 추가 */
    @Transactional
    public GuardianResponse addGuardian(Long userId, GuardianRequest request) {
        User user = userService.getActiveUserForUpdate(userId);
        return GuardianResponse.from(save(user, request));
    }

    /** GET /api/guardians — 로그인한 사용자의 보호자 목록 (등록 순) */
    @Transactional(readOnly = true)
    public List<GuardianResponse> getGuardians(Long userId) {
        userService.getActiveUser(userId);
        return guardianRepository.findByUser_UserIdAndDeletedAtIsNullOrderByGuardianIdAsc(userId).stream()
                .map(GuardianResponse::from)
                .toList();
    }

    /** DELETE /api/guardians/{guardianId} — 로그인한 사용자의 보호자 1명 삭제 */
    @Transactional
    public void deleteGuardian(Long userId, Long guardianId) {
        User user = userService.getActiveUserForUpdate(userId);

        Guardian guardian = guardianRepository
                .findByGuardianIdAndUser_UserIdAndDeletedAtIsNull(guardianId, userId)
                .orElseThrow(() -> new GuardianNotFoundException("보호자를 찾을 수 없습니다."));

        if (user.getUserType().isMinor()
                && guardianRepository.countByUser_UserIdAndDeletedAtIsNull(userId) <= MIN_GUARDIANS_FOR_MINOR) {
            throw new IllegalArgumentException(
                    "미성년자는 보호자를 최소 " + MIN_GUARDIANS_FOR_MINOR + "명 유지해야 합니다. 다른 보호자를 먼저 추가한 뒤 삭제해 주세요.");
        }

        guardian.delete();
    }

    /**
     * 회원가입 시 보호자 목록 저장. {@link com.safewalk.auth.AuthService#signup}의 트랜잭션 안에서 호출되므로
     * 여기서 예외가 나면 사용자 계정 생성까지 함께 취소된다.
     */
    @Transactional
    public void addGuardiansOnSignup(User user, List<GuardianRequest> requests) {
        if (requests.size() > MAX_GUARDIANS) {
            throw new IllegalArgumentException("보호자는 최대 " + MAX_GUARDIANS + "명까지 등록할 수 있습니다.");
        }
        for (GuardianRequest request : requests) {
            save(user, request);
        }
    }

    private Guardian save(User user, GuardianRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("보호자 정보가 비어 있습니다.");
        }
        String name = request.name().trim();
        if (name.isBlank() || request.phone().isBlank() || request.relationship().isBlank()) {
            throw new IllegalArgumentException("보호자의 이름, 전화번호, 관계를 모두 입력해 주세요.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("보호자 이름은 " + NAME_MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        String phone = PhoneNumbers.normalize(request.phone(), "보호자 전화번호");
        Relationship relationship = Relationship.from(request.relationship());

        if (phone.equals(user.getPhone())) {
            throw new IllegalArgumentException("본인 전화번호는 보호자로 등록할 수 없습니다.");
        }
        // 아래 조회는 같은 트랜잭션에서 앞서 저장한 보호자도 포함한다(조회 전 자동 flush).
        if (guardianRepository.countByUser_UserIdAndDeletedAtIsNull(user.getUserId()) >= MAX_GUARDIANS) {
            throw new IllegalArgumentException("보호자는 최대 " + MAX_GUARDIANS + "명까지 등록할 수 있습니다.");
        }
        if (guardianRepository.existsByUser_UserIdAndPhoneAndDeletedAtIsNull(user.getUserId(), phone)) {
            throw new DuplicateGuardianException("이미 등록된 보호자 전화번호입니다: " + phone);
        }

        return guardianRepository.save(new Guardian(user, name, phone, relationship));
    }
}
