package com.safewalk.report;

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
 * 사용자 위험 제보 원본 (risk_report 테이블). 사진은 {@link RiskReportImage}(1:N)에서 관리한다.
 *
 * risk_area_id는 승인 기능(관리자)에서 다룰 예정이라 아직 RiskArea 엔티티와 연결하지 않고 ID 값만 둔다.
 */
@Entity
@Table(name = "risk_report")
public class RiskReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_type", nullable = false, length = 50)
    private RiskType riskType;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lng", nullable = false)
    private double lng;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "risk_area_id")
    private Long riskAreaId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 전용 기본 생성자 */
    protected RiskReport() {
    }

    public RiskReport(User user, RiskType riskType, double lat, double lng,
                      String address, String description, boolean anonymous) {
        this.user = user;
        this.riskType = riskType;
        this.lat = lat;
        this.lng = lng;
        this.address = address;
        this.description = description;
        this.anonymous = anonymous;
        this.status = ReportStatus.PENDING;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getReportId() {
        return reportId;
    }

    public User getUser() {
        return user;
    }

    public RiskType getRiskType() {
        return riskType;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public String getAddress() {
        return address;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Long getRiskAreaId() {
        return riskAreaId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** 사진 추가·삭제 등 제보 내용이 바뀐 시각을 기록한다. */
    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
