package com.safewalk.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자 유형 (MVP 기준: ADULT, MINOR 2종).
 * 유형별 안전시설 가중치(FACILITY_TYPE_WEIGHT) 적용 로직은 아직 구현하지 않았다 —
 * 지금은 회원가입 시 사용자가 어느 유형에 속하는지 저장하는 뼈대만 존재한다.
 */
@Entity
@Table(name = "user_type")
public class UserType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_type_id")
    private Long userTypeId;

    @Column(name = "type_name", nullable = false, unique = true, length = 30)
    private String typeName = "";

    /** JPA 전용 기본 생성자 */
    protected UserType() {
    }

    public UserType(String typeName) {
        this.typeName = typeName;
    }

    public Long getUserTypeId() {
        return userTypeId;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }
}
