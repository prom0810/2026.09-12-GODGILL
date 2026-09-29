package com.safewalk.auth;

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
 * 현 단계 범위:
 *  - 이메일·비밀번호·사용자 유형(ADULT/MINOR)만 다룬다.
 *  - 이메일 형식, 비밀번호 길이 등 세부 검증은 하지 않는다 (빈 값 여부만 확인).
 *  - 중복 가입 여부는 이메일로만 판단한다.
 *  - 사용자 유형별 가중치 등 실제 활용 로직은 아직 없다 — 유형은 저장만 해두는 뼈대 단계.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(
            UserRepository userRepository,
            UserTypeRepository userTypeRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider
    ) {
        this.userRepository = userRepository;
        this.userTypeRepository = userTypeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.email().trim();
        String password = request.password();

        if (email.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("이메일과 비밀번호를 입력해 주세요.");
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new DuplicateEmailException("이미 가입된 이메일입니다.");
        }

        UserType userType = userTypeRepository
                .findByTypeName(request.userType().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 사용자 유형입니다: " + request.userType()));

        // TODO: 이름·전화번호 입력 화면이 추가되면 이 값을 실제 입력값으로 교체한다.
        //       users.name / users.phone이 DB에서 NOT NULL이라 지금은 임시값을 채워 넣는다.
        User user = new User(
                userType,
                tempNameFrom(email),
                email,
                passwordEncoder.encode(password),
                "000-0000-0000"
        );
        User saved = userRepository.save(user);
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

    /** 이메일의 '@' 앞부분을 임시 이름으로 사용한다. 비어 있으면 "user". */
    private static String tempNameFrom(String email) {
        int at = email.indexOf('@');
        String local = at >= 0 ? email.substring(0, at) : email;
        return local.isBlank() ? "user" : local;
    }
}
