package com.safewalk.route;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SegmentFacilityRepository extends JpaRepository<SegmentFacility, Long> {

    List<SegmentFacility> findBySegment_SegmentIdInOrderBySegmentFacilityIdAsc(Collection<Long> segmentIds);
}
