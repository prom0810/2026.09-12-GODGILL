package com.safewalk.auth;

import com.safewalk.global.PhoneNumbers;
import com.safewalk.guardian.GuardianService;
import com.safewalk.user.User;
import com.safewalk.user.UserRepository;
import com.safewalk.user.UserType;
import com.safewalk.user.UserTypeRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입/로그인 처리.
 *
 * 회원가입 규칙:
 *  - email·password·name·phone·userType 모두 필수 (빈 값이면 400)
 *  - 전화번호는 {@link PhoneNumbers}로 "010-1234-5678" 형태로 정규화해서 저장
 *  - 이메일 중복 → 409, 전화번호 중복 → 409
 *  - MINOR(미성년자)는 보호자 1명 이상 필수 (없으면 400)
 *  - 보호자 목록은 사용자와 같은 트랜잭션에서 저장한다. 보호자 저장이 실패하면 계정도 생성되지 않는다.
 *
 * 이메일 형식, 비밀번호 길이 등 세부 검증은 아직 하지 않는다.
 */
@Service
public class AuthService {

    private static final int NAME_MAX_LENGTH = 50;

    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final GuardianService guardianService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(
            UserRepository userRepository,
            UserTypeRepository userTypeRepository,
            GuardianService guardianService,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider
    ) {
        this.userRepository = userRepository;
        this.userTypeRepository = userTypeRepository;
        this.guardianService = guardianService;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.email().trim();
        String password = request.password();
        String name = request.name().trim();

        if (email.isBlank() || password.isBlank() || name.isBlank()
                || request.phone().isBlank() || request.userType().isBlank()) {
            throw new IllegalArgumentException("이메일, 비밀번호, 이름, 전화번호, 사용자 유형을 모두 입력해 주세요.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("이름은 " + NAME_MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        String phone = PhoneNumbers.normalize(request.phone(), "전화번호");

        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new DuplicateEmailException("이미 가입된 이메일입니다.");
        }
        if (userRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new DuplicatePhoneException("이미 가입된 전화번호입니다.");
        }

        UserType userType = userTypeRepository
                .findByTypeName(request.userType().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 사용자 유형입니다: " + request.userType()));

        if (userType.isMinor() && request.guardians().isEmpty()) {
            throw new IllegalArgumentException("미성년자(MINOR)는 보호자를 1명 이상 등록해야 합니다.");
        }

        User user = new User(userType, name, email, passwordEncoder.encode(password), phone);
        User saved = userRepository.save(user);

        guardianService.addGuardiansOnSignup(saved, request.guardians());

        String token = jwtProvider.createToken(saved.getUserId(), saved.getEmail(), userType.getTypeName());
        return new AuthResponse(saved.getUserId(), saved.getEmail(), userType.getTypeName(), token);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim();
        String password = request.password();

        if (email.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("이메일과 비밀번호를 입력해 주세요.");
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String typeName = user.getUserType().getTypeName();
        String token = jwtProvider.createToken(user.getUserId(), user.getEmail(), typeName);
        return new AuthResponse(user.getUserId(), user.getEmail(), typeName, token);
    }
}
