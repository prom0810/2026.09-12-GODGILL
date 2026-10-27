package com.safewalk.ai.roadimage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadImageAnalysisRepository extends JpaRepository<RoadImageAnalysis, Long> {

    List<RoadImageAnalysis> findAllByOrderByImageAnalysisIdAsc();
}
