package com.safewalk.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 위험구역 (risk_area 테이블). 중심 좌표 + 반경(m)의 원형 구역.
 * 앱이 경로 안전도 감점(risk_score)과 안내 중 위험구역 접근·진입 알림에 쓴다.
 * 지금은 조회만 한다 (등록·해결 처리는 관리자 기능에서 다룰 예정).
 */
@Entity
@Table(name = "risk_area")
public class RiskArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_area_id")
    private Long riskAreaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_type", nullable = false, length = 50)
    private RiskType riskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private RiskAreaSource source = RiskAreaSource.USER_REPORT;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lng", nullable = false)
    private double lng;

    /** 단위 m */
    @Column(name = "radius", nullable = false)
    private double radius;

    @Column(name = "description", length = 500)
    private String description;

    /** 경로 구간 감점 최대값 (구역 중심에서 최대, 경계에서 0으로 줄어든다) */
    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RiskAreaStatus status = RiskAreaStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 전용 기본 생성자 */
    protected RiskArea() {
    }

    public Long getRiskAreaId() {
        return riskAreaId;
    }

    public RiskType getRiskType() {
        return riskType;
    }

    public RiskAreaSource getSource() {
        return source;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public double getRadius() {
        return radius;
    }

    public String getDescription() {
        return description;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public RiskAreaStatus getStatus() {
        return status;
    }
}
