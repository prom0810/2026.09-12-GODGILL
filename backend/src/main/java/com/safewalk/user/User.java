package com.safewalk.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 앱 사용자 계정 (DB 설계서의 USER 엔티티. 테이블명은 Postgres 예약어 회피를 위해 users).
 *
 * name·phone은 DB에서 NOT NULL이지만 현재 회원가입 화면은 이메일·비밀번호·사용자 유형만 입력받는다.
 * 이름·전화번호 입력 화면이 추가되기 전까지는 {@link com.safewalk.auth.AuthService}에서 임시값을 채워 넣는다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_type_id", nullable = false)
    private UserType userType;

    @Column(name = "name", nullable = false, length = 50)
    private String name = "";

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email = "";

    @Column(name = "password", nullable = false, length = 255)
    private String password = "";

    @Column(name = "phone", nullable = false, length = 20)
    private String phone = "";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /** JPA 전용 기본 생성자 */
    protected User() {
    }

    public User(UserType userType, String name, String email, String password, String phone) {
        this.userType = userType;
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
