package com.safewalk.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 앱 사용자 계정 (DB 설계서의 USER 엔티티. 테이블명은 Postgres 예약어 회피를 위해 users).
 *
 * name·phone은 DB에서 NOT NULL이지만 현재 회원가입 화면은 이메일·비밀번호·사용자 유형만 입력받는다.
 * 이름·전화번호 입력 화면이 추가되기 전까지는 [com.safewalk.auth.AuthService]에서 임시값을 채워 넣는다.
 */
@Entity
@Table(name = "users")
class User(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_type_id", nullable = false)
    var userType: UserType = UserType(),

    @Column(name = "name", nullable = false, length = 50)
    var name: String = "",

    @Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String = "",

    @Column(name = "password", nullable = false, length = 255)
    var password: String = "",

    @Column(name = "phone", nullable = false, length = 20)
    var phone: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    var userId: Long = 0
}
