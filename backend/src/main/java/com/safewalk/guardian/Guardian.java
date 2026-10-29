package com.safewalk.guardian;

import com.safewalk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 사용자의 보호자/지인 (DB v3 guardian 테이블, users와 N:1).
 *
 * - user: 보호 대상 사용자 (이 보호자를 등록한 사람)
 * - guardianUser: 보호자의 앱 계정. 연결 전(UNREGISTERED/INVITED)에는 null일 수 있다
 * - primary: 대표 보호자. 사용자당 삭제되지 않은 대표 보호자는 최대 1명 (DB uq_guardian_primary_active)
 * - canViewLocation: 보호자가 사용자의 위치를 볼 수 있는지 (기본 false)
 *
 * 한 사용자당 최대 {@link GuardianService#MAX_GUARDIANS}명. 삭제는 soft delete(deleted_at 기록) 방식이다.
 * 상태 변경은 setter 대신 아래 의미 있는 메서드로만 한다.
 */
@Entity
@Table(name = "guardian")
public class Guardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guardian_id")
    private Long guardianId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_user_id")
    private User guardianUser;

    @Column(name = "name", nullable = false, length = 50)
    private String name = "";

    /** "010-1234-5678" 형태로 정규화된 값 */
    @Column(name = "phone", nullable = false, length = 20)
    private String phone = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 30)
    private Relationship relationship;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 20)
    private RegistrationStatus registrationStatus = RegistrationStatus.UNREGISTERED;

    @Column(name = "can_view_location", nullable = false)
    private boolean canViewLocation = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "connected_at")
    private OffsetDateTime connectedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /** JPA 전용 기본 생성자 */
    protected Guardian() {
    }

    public Guardian(User user, String name, String phone, Relationship relationship, boolean canViewLocation) {
        this.user = user;
        this.name = name;
        this.phone = phone;
        this.relationship = relationship;
        this.canViewLocation = canViewLocation;
        this.registrationStatus = RegistrationStatus.UNREGISTERED;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getGuardianId() {
        return guardianId;
    }

    public User getUser() {
        return user;
    }

    public User getGuardianUser() {
        return guardianUser;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public boolean isPrimary() {
        return primary;
    }

    public RegistrationStatus getRegistrationStatus() {
        return registrationStatus;
    }

    public boolean isCanViewLocation() {
        return canViewLocation;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getConnectedAt() {
        return connectedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    /** 이름·관계 변경 (PATCH /api/guardians/{id}). 값 검증은 GuardianService에서 한다. */
    public void changeName(String name) {
        this.name = name;
    }

    public void changeRelationship(Relationship relationship) {
        this.relationship = relationship;
    }

    public void changeCanViewLocation(boolean canViewLocation) {
        this.canViewLocation = canViewLocation;
    }

    /** 대표 보호자 지정/해제. "사용자당 1명" 규칙은 GuardianService가 맞춘다. */
    public void markPrimary() {
        this.primary = true;
    }

    public void unmarkPrimary() {
        this.primary = false;
    }

    // ── 계정 연결 상태 전이 ─────────────────────────────────────────────
    //  UNREGISTERED/REVOKED ──invite──▶ INVITED ──accept──▶ CONNECTED ──revoke/leave──▶ REVOKED
    //                                   INVITED ──reject/cancelInvite──▶ UNREGISTERED
    // 허용되지 않는 상태에서 호출하면 GuardianStateException(409).

    /** 연결 초대 보내기 (보호받는 사용자가 호출). 해제된 보호자도 다시 초대할 수 있다. */
    public void invite() {
        if (registrationStatus == RegistrationStatus.INVITED) {
            throw new GuardianStateException("이미 초대를 보낸 보호자입니다.");
        }
        if (registrationStatus == RegistrationStatus.CONNECTED) {
            throw new GuardianStateException("이미 연결된 보호자입니다.");
        }
        this.registrationStatus = RegistrationStatus.INVITED;
        this.guardianUser = null;
        this.connectedAt = null;
    }

    /** 보낸 초대 취소 (보호받는 사용자가 호출) */
    public void cancelInvite() {
        requireStatus(RegistrationStatus.INVITED, "초대 중인 보호자가 아닙니다.");
        this.registrationStatus = RegistrationStatus.UNREGISTERED;
    }

    /** 초대 수락 (보호자가 자기 계정으로 호출). DB 제약상 CONNECTED에는 guardian_user_id·connected_at이 필수다. */
    public void accept(User guardianAccount) {
        requireStatus(RegistrationStatus.INVITED, "수락할 수 있는 초대가 없습니다.");
        this.guardianUser = guardianAccount;
        this.registrationStatus = RegistrationStatus.CONNECTED;
        this.connectedAt = OffsetDateTime.now();
    }

    /** 초대 거절 (보호자가 호출) */
    public void reject() {
        requireStatus(RegistrationStatus.INVITED, "거절할 수 있는 초대가 없습니다.");
        this.registrationStatus = RegistrationStatus.UNREGISTERED;
    }

    /** 연결 해제 (보호받는 사용자 또는 보호자가 호출). 연결 이력(guardian_user_id, connected_at)은 남긴다. */
    public void revoke() {
        requireStatus(RegistrationStatus.CONNECTED, "연결된 보호자가 아닙니다.");
        this.registrationStatus = RegistrationStatus.REVOKED;
    }

    private void requireStatus(RegistrationStatus expected, String message) {
        if (registrationStatus != expected) {
            throw new GuardianStateException(message);
        }
    }

    /**
     * soft delete: 행은 남기고 deleted_at만 기록한다.
     * 초대 중이거나 연결된 보호자였다면 연결도 끊는다(REVOKED). 연결 이력(guardian_user_id, connected_at)은 남긴다.
     */
    public void delete() {
        if (registrationStatus == RegistrationStatus.INVITED
                || registrationStatus == RegistrationStatus.CONNECTED) {
            this.registrationStatus = RegistrationStatus.REVOKED;
        }
        this.primary = false;
        this.deletedAt = OffsetDateTime.now();
    }
}
