package com.safewalk.report;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAreaRepository extends JpaRepository<RiskArea, Long> {

    List<RiskArea> findByStatusOrderByRiskAreaIdAsc(RiskAreaStatus status);
}
