package com.safewalk.route;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutePointRepository extends JpaRepository<RoutePoint, Long> {

    List<RoutePoint> findByRoute_RouteIdOrderBySeqAsc(Long routeId);

    /** 목록 화면용: 여러 경로의 출발·도착 지점만 한 번에 */
    List<RoutePoint> findByRoute_RouteIdInAndPointTypeIn(Collection<Long> routeIds, Collection<RoutePointType> types);
}
