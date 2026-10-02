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
 * 사용자의 보호자/지인 (DB 설계서의 GUARDIAN 엔티티, users와 N:1).
 * 한 사용자당 최대 {@link GuardianService#MAX_GUARDIANS}명. 삭제는 soft delete(deleted_at 기록) 방식이다.
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

    @Column(name = "name", nullable = false)
    private String name = "";

    /** "010-1234-5678" 형태로 정규화된 값 */
    @Column(name = "phone", nullable = false)
    private String phone = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false)
    private Relationship relationship;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /** JPA 전용 기본 생성자 */
    protected Guardian() {
    }

    public Guardian(User user, String name, String phone, Relationship relationship) {
        this.user = user;
        this.name = name;
        this.phone = phone;
        this.relationship = relationship;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getGuardianId() {
        return guardianId;
    }

    public User getUser() {
        return user;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    /** soft delete: 행은 남기고 deleted_at만 기록한다. */
    public void delete() {
        this.deletedAt = OffsetDateTime.now();
    }
}
