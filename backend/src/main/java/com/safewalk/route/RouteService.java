package com.safewalk.route;

import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경로 안내 기록: 안내 시작 저장 → 도착/취소 → 기록 조회.
 *
 * 경로 계산은 앱(RouteAStar)이 한다. 서버는 사용자가 "안내 시작"을 누른 경로만 저장한다
 * (DB 설계: route = 안내를 시작한 경로만).
 *
 * 규칙
 *  - 진행 중(IN_PROGRESS) 경로는 사용자당 1개. 새 안내를 시작하면 기존 것은 자동 CANCELLED
 *    (409로 막으면 앱이 비정상 종료됐을 때 사용자가 새 안내를 못 하게 되므로)
 *  - 도착(COMPLETED)·취소(CANCELLED)는 IN_PROGRESS에서만 가능. 아니면 409
 *  - 남의 경로는 404 (존재 여부를 드러내지 않음)
 *  - 오래 방치된 진행 중 경로 자동 취소, 보관 기간 정리는 {@link RouteCleanupJob}
 */
@Service
public class RouteService {

    public static final int MAX_PAGE_SIZE = 50;
    private static final EnumSet<RoutePointType> ENDPOINTS = EnumSet.of(RoutePointType.START, RoutePointType.END);

    private final RouteRepository routeRepository;
    private final RoutePointRepository pointRepository;
    private final RouteSegmentRepository segmentRepository;
    private final SegmentFacilityRepository facilityRepository;
    private final RouteWriter routeWriter;
    private final UserService userService;

    public RouteService(RouteRepository routeRepository, RoutePointRepository pointRepository,
                        RouteSegmentRepository segmentRepository, SegmentFacilityRepository facilityRepository,
                        RouteWriter routeWriter, UserService userService) {
        this.routeRepository = routeRepository;
        this.pointRepository = pointRepository;
        this.segmentRepository = segmentRepository;
        this.facilityRepository = facilityRepository;
        this.routeWriter = routeWriter;
        this.userService = userService;
    }

    /** POST /api/routes — 안내 시작 */
    @Transactional
    public RouteResponses.Started start(Long userId, RouteRequests.Start request) {
        // 검증은 DB 작업 전에 끝낸다 (잘못된 요청이 기존 경로를 취소하지 않게)
        RouteDraft draft = RouteDraft.from(request);

        // 같은 사용자의 안내 시작이 동시에 와도 하나씩 처리되게 사용자 행을 잠근다
        User user = userService.getActiveUserForUpdate(userId);

        Long cancelledRouteId = null;
        for (Route open : routeRepository.findByUser_UserIdAndStatusOrderByCreatedAtDesc(userId, RouteStatus.IN_PROGRESS)) {
            open.cancel();
            if (cancelledRouteId == null) {
                cancelledRouteId = open.getRouteId();
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        long routeId = routeWriter.insert(userId, user.getUserType().getUserTypeId(), draft, now);
        return new RouteResponses.Started(
                routeId,
                RouteStatus.IN_PROGRESS.name(),
                draft.totalDistance(),
                draft.estimatedTime(),
                draft.safetyScore(),
                draft.points().size(),
                draft.segments().size(),
                draft.facilityCount(),
                now,
                cancelledRouteId
        );
    }

    /** GET /api/routes/active — 진행 중 경로. 없으면 null */
    @Transactional(readOnly = true)
    public RouteResponses.Summary getActive(Long userId) {
        userService.getActiveUser(userId);
        return routeRepository.findByUser_UserIdAndStatusOrderByCreatedAtDesc(userId, RouteStatus.IN_PROGRESS)
                .stream()
                .findFirst()
                .map(route -> summaries(List.of(route)).get(0))
                .orElse(null);
    }

    /** GET /api/routes?page=0&size=20 — 내 경로 기록 (최신순) */
    @Transactional(readOnly = true)
    public RouteResponses.Page getRoutes(Long userId, int page, int size) {
        userService.getActiveUser(userId);
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        Slice<Route> routes = routeRepository.findByUser_UserIdOrderByCreatedAtDescRouteIdDesc(
                userId, PageRequest.of(page, size));
        return new RouteResponses.Page(summaries(routes.getContent()), page, size, routes.hasNext());
    }

    /** GET /api/routes/{routeId} — 지점·구간·시설까지 */
    @Transactional(readOnly = true)
    public RouteResponses.Detail getRoute(Long userId, Long routeId) {
        userService.getActiveUser(userId);
        Route route = findOwned(userId, routeId);

        List<RoutePoint> points = pointRepository.findByRoute_RouteIdOrderBySeqAsc(routeId);
        List<RouteSegment> segments = segmentRepository.findByRouteIdOrdered(routeId);
        Map<Long, List<RouteResponses.Facility>> facilitiesBySegment = segments.isEmpty() ? Map.of()
                : facilityRepository.findBySegment_SegmentIdInOrderBySegmentFacilityIdAsc(
                                segments.stream().map(RouteSegment::getSegmentId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(f -> f.getSegment().getSegmentId(),
                                Collectors.mapping(RouteResponses.Facility::from, Collectors.toList())));

        RoutePoint start = null;
        RoutePoint end = null;
        for (RoutePoint p : points) {
            if (p.getPointType() == RoutePointType.START) {
                start = p;
            } else if (p.getPointType() == RoutePointType.END) {
                end = p;
            }
        }

        List<RouteResponses.Segment> segmentResponses = segments.stream()
                .map(s -> new RouteResponses.Segment(
                        s.getSegmentId(),
                        s.getStartPoint().getSeq(),
                        s.getDistance(),
                        s.getSafetyScore(),
                        s.getRiskScore(),
                        facilitiesBySegment.getOrDefault(s.getSegmentId(), List.of())))
                .toList();

        return new RouteResponses.Detail(
                RouteResponses.Summary.of(route, start, end),
                !segments.isEmpty(),
                points.stream().map(RouteResponses.Point::from).toList(),
                segmentResponses
        );
    }

    /** PATCH /api/routes/{routeId} — 도착(COMPLETED) 또는 안내 취소(CANCELLED) */
    @Transactional
    public RouteResponses.Summary updateStatus(Long userId, Long routeId, RouteRequests.UpdateStatus request) {
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("status(COMPLETED 또는 CANCELLED)를 입력해 주세요.");
        }
        userService.getActiveUserForUpdate(userId);
        Route route = findOwned(userId, routeId);
        switch (request.status()) {
            case COMPLETED -> route.complete(OffsetDateTime.now());
            case CANCELLED -> route.cancel();
            default -> throw new IllegalArgumentException("status는 COMPLETED 또는 CANCELLED만 보낼 수 있습니다.");
        }
        return summaries(List.of(route)).get(0);
    }

    private Route findOwned(Long userId, Long routeId) {
        return routeRepository.findByRouteIdAndUser_UserId(routeId, userId)
                .orElseThrow(() -> new RouteNotFoundException("경로를 찾을 수 없습니다."));
    }

    /** 경로 목록에 출발·도착 지점을 붙인다 (쿼리 1번) */
    private List<RouteResponses.Summary> summaries(List<Route> routes) {
        if (routes.isEmpty()) {
            return List.of();
        }
        Map<Long, RoutePoint> starts = new HashMap<>();
        Map<Long, RoutePoint> ends = new HashMap<>();
        List<Long> ids = routes.stream().map(Route::getRouteId).toList();
        for (RoutePoint p : pointRepository.findByRoute_RouteIdInAndPointTypeIn(ids, ENDPOINTS)) {
            Long routeId = p.getRoute().getRouteId();
            (p.getPointType() == RoutePointType.START ? starts : ends).put(routeId, p);
        }
        return routes.stream()
                .map(r -> RouteResponses.Summary.of(r, starts.get(r.getRouteId()), ends.get(r.getRouteId())))
                .toList();
    }
}
