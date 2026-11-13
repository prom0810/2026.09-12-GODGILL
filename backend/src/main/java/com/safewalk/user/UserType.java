package com.safewalk.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자 유형 (MVP 기준: ADULT, MINOR 2종).
 * 회원·보호자 처리에 사용하는 만 19세 기준 유형이다.
 * 경로 시설 가중치는 별도의 RouteAgeBand(14세 기준)와 route_facility_weight를 사용한다.
 */
@Entity
@Table(name = "user_type")
public class UserType {

    public static final String MINOR = "MINOR";

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

    /** 미성년자 유형 여부. 미성년자는 보호자를 최소 1명 유지해야 한다. */
    public boolean isMinor() {
        return MINOR.equals(typeName);
    }
}
