package com.safewalk.report;

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
 * 제보 사진 메타데이터 (risk_report_image 테이블). 파일 자체는 이미지 저장소(Supabase Storage)에 있다.
 *
 * storage_path에는 URL이 아니라 저장소 안의 object key만 저장한다. 예: "reports/12/3f2a...e1.jpg"
 * 제보(risk_report)가 삭제되면 DB 행은 ON DELETE CASCADE로 함께 지워지지만, 저장소 파일은 지워지지 않으므로
 * 제보 삭제 기능을 만들 때는 {@link RiskReportImageService}를 통해 파일도 함께 지워야 한다.
 */
@Entity
@Table(name = "risk_report_image")
public class RiskReportImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private RiskReport report;

    @Column(name = "storage_path", nullable = false, unique = true, length = 500)
    private String storagePath;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /** JPA 전용 기본 생성자 */
    protected RiskReportImage() {
    }

    public RiskReportImage(RiskReport report, String storagePath) {
        this.report = report;
        this.storagePath = storagePath;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getImageId() {
        return imageId;
    }

    public RiskReport getReport() {
        return report;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
