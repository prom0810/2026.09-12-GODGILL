package com.safewalk.route;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteSegmentRepository extends JpaRepository<RouteSegment, Long> {

    /** 경로의 구간을 순서대로. route_segment에 route_id가 없어서 시작 지점으로 찾는다 */
    @Query("""
            select s from RouteSegment s
            join fetch s.startPoint sp
            join fetch s.endPoint
            where sp.route.routeId = :routeId
            order by sp.seq
            """)
    List<RouteSegment> findByRouteIdOrdered(@Param("routeId") Long routeId);
}
