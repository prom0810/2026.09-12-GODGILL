package com.safewalk.route;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {

    /** 진행 중인 경로 (정상이면 0~1개. 예전 데이터 대비로 목록으로 받는다) */
    List<Route> findByUser_UserIdAndStatusOrderByCreatedAtDesc(Long userId, RouteStatus status);

    /** 경로 안내 중인지 (웨어러블 연결 전환 규칙에서 사용) */
    boolean existsByUser_UserIdAndStatus(Long userId, RouteStatus status);

    Optional<Route> findByRouteIdAndUser_UserId(Long routeId, Long userId);

    /** 내 경로 기록, 최신순 (인덱스 ix_route_user_created) */
    Slice<Route> findByUser_UserIdOrderByCreatedAtDescRouteIdDesc(Long userId, Pageable pageable);
}
