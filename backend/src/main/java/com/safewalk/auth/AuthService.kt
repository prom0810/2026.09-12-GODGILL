package com.safewalk.auth

import com.safewalk.user.User
import com.safewalk.user.UserRepository
import com.safewalk.user.UserTypeRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
class AuthService(
    private val userRepository: UserRepository,
    private val userTypeRepository: UserTypeRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider
) {

    @Transactional
    fun signup(request: SignupRequest): AuthResponse {
        val email = request.email.trim()
        val password = request.password

        if (email.isBlank() || password.isBlank()) {
            throw IllegalArgumentException("이메일과 비밀번호를 입력해 주세요.")
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw DuplicateEmailException("이미 가입된 이메일입니다.")
        }

        val userType = userTypeRepository.findByTypeName(request.userType.trim().uppercase())
            ?: throw IllegalArgumentException("알 수 없는 사용자 유형입니다: ${request.userType}")

        val user = User(
            userType = userType,
            // TODO: 이름·전화번호 입력 화면이 추가되면 이 값을 실제 입력값으로 교체한다.
            //       users.name / users.phone이 DB에서 NOT NULL이라 지금은 임시값을 채워 넣는다.
            name = email.substringBefore("@").ifBlank { "user" },
            email = email,
            password = passwordEncoder.encode(password),
            phone = "000-0000-0000"
        )
        val saved = userRepository.save(user)
        val token = jwtProvider.createToken(saved.userId, saved.email, userType.typeName)
        return AuthResponse(saved.userId, saved.email, userType.typeName, token)
    }

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthResponse {
        val email = request.email.trim()
        val password = request.password

        if (email.isBlank() || password.isBlank()) {
            throw IllegalArgumentException("이메일과 비밀번호를 입력해 주세요.")
        }

        val user = userRepository.findByEmailAndDeletedAtIsNull(email)
            ?: throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")

        if (!passwordEncoder.matches(password, user.password)) {
            throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")
        }

        val token = jwtProvider.createToken(user.userId, user.email, user.userType.typeName)
        return AuthResponse(user.userId, user.email, user.userType.typeName, token)
    }
}
