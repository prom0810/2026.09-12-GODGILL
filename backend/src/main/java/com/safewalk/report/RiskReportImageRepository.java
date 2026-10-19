package com.safewalk.report;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskReportImageRepository extends JpaRepository<RiskReportImage, Long> {

    List<RiskReportImage> findByReport_ReportIdOrderByImageIdAsc(Long reportId);

    long countByReport_ReportId(Long reportId);

    Optional<RiskReportImage> findByImageIdAndReport_ReportId(Long imageId, Long reportId);
}
