package com.safewalk.sensor;

import com.safewalk.location.GpsPoint;
import com.safewalk.location.RedisGpsStore;
import com.safewalk.route.Route;
import com.safewalk.route.RouteRepository;
import com.safewalk.route.RouteSegment;
import com.safewalk.route.RouteSegmentRepository;
import com.safewalk.route.RouteStatus;
import com.safewalk.wearable.WearableDevice;
import com.safewalk.wearable.WearableDeviceRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 워치 자동 감지 SOS(FALL·ABNORMAL_HEART_RATE)의 맥락을 서버가 정한다.
 * DB 제약 ck_emergency_auto_detect_context 때문에 wearable_id·segment_id가 꼭 필요하다.
 *
 *  - route   : 내 진행 중(IN_PROGRESS) 경로. 없으면 empty (안내 중이 아닐 때의 감지는 받지 않는다)
 *  - wearable: 지금 연결 중인 내 워치. 없으면 empty
 *  - segment : 감지 위치(요청 좌표 → 없으면 Redis 최신 GPS)에서 가장 가까운 구간. 위치를 모르면 첫 구간
 */
@Component
public class SensorContextResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensorContextResolver.class);

    public record Context(long routeId, long wearableId, long segmentId) {
    }

    public enum Missing { NO_ROUTE, NO_WEARABLE, NO_SEGMENT }

    public record Result(Context context, Missing missing) {
    }

    private final RouteRepository routeRepository;
    private final RouteSegmentRepository segmentRepository;
    private final WearableDeviceRepository wearableRepository;
    private final RedisGpsStore gpsStore;

    public SensorContextResolver(RouteRepository routeRepository, RouteSegmentRepository segmentRepository,
                                 WearableDeviceRepository wearableRepository, RedisGpsStore gpsStore) {
        this.routeRepository = routeRepository;
        this.segmentRepository = segmentRepository;
        this.wearableRepository = wearableRepository;
        this.gpsStore = gpsStore;
    }

    public Result resolve(Long userId, Double lat, Double lng) {
        List<Route> routes = routeRepository.findByUser_UserIdAndStatusOrderByCreatedAtDesc(userId, RouteStatus.IN_PROGRESS);
        if (routes.isEmpty()) {
            return new Result(null, Missing.NO_ROUTE);
        }
        Optional<WearableDevice> wearable = wearableRepository.findByUser_UserIdAndActiveTrueAndDeletedAtIsNull(userId)
                .stream().findFirst();
        if (wearable.isEmpty()) {
            return new Result(null, Missing.NO_WEARABLE);
        }
        Route route = routes.get(0);
        List<RouteSegment> segments = segmentRepository.findByRouteIdOrdered(route.getRouteId());
        if (segments.isEmpty()) {
            return new Result(null, Missing.NO_SEGMENT);
        }
        SegmentMatcher matcher = matcher(segments);
        long segmentId = matcher.first().segmentId();
        if (lat == null) {
            try {
                Optional<GpsPoint> latest = gpsStore.latest(userId);
                if (latest.isPresent()) {
                    lat = latest.get().lat();
                    lng = latest.get().lng();
                }
            } catch (RuntimeException e) {
                LOGGER.warn("자동 감지 SOS 위치 조회 실패 (userId={}), 첫 구간으로 기록", userId, e);
            }
        }
        if (lat != null && lng != null) {
            segmentId = matcher.match(lat, lng).segmentId();
        }
        return new Result(new Context(route.getRouteId(), wearable.get().getWearableId(), segmentId), null);
    }

    static SegmentMatcher matcher(List<RouteSegment> segments) {
        return new SegmentMatcher(segments.stream()
                .map(s -> new SegmentMatcher.Segment(s.getSegmentId(), s.getStartPoint().getLat(),
                        s.getStartPoint().getLng(), s.getEndPoint().getLat(), s.getEndPoint().getLng(), s.getDistance()))
                .toList());
    }
}
