package com.safewalk.roadimage;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoadImageAnalysisService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double MAX_RADIUS_METERS = 20_000.0;
    private static final int MAX_RESULT_LIMIT = 200;

    private final RoadImageAnalysisRepository repository;

    public RoadImageAnalysisService(RoadImageAnalysisRepository repository) {
        this.repository = repository;
    }

    /** CSV 적재 여부 확인을 위한 전체 조회. */
    @Transactional(readOnly = true)
    public List<RoadImageAnalysisResponse> findAll() {
        return repository.findAllByOrderByImageAnalysisIdAsc().stream()
                .map(RoadImageAnalysisResponse::from)
                .toList();
    }

    /**
     * 주어진 좌표에서 radiusMeters 이내의 분석 결과를 가까운 순서로 반환한다.
     * 현재 데이터가 88건으로 작아 전체 행의 정확한 구면거리를 계산한다.
     */
    @Transactional(readOnly = true)
    public List<NearbyRoadImageAnalysisResponse> findNearby(
            double latitude,
            double longitude,
            double radiusMeters,
            int limit
    ) {
        validateSearch(latitude, longitude, radiusMeters, limit);

        return repository.findAll().stream()
                .map(analysis -> new DistanceResult(
                        analysis,
                        distanceMeters(latitude, longitude, analysis.getLatitude(), analysis.getLongitude())))
                .filter(result -> result.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingDouble(DistanceResult::distanceMeters)
                        .thenComparing(result -> result.analysis().getImageAnalysisId()))
                .limit(limit)
                .map(result -> new NearbyRoadImageAnalysisResponse(
                        RoadImageAnalysisResponse.from(result.analysis()),
                        result.distanceMeters()))
                .toList();
    }

    private static void validateSearch(double latitude, double longitude, double radiusMeters, int limit) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("위치 값이 올바르지 않습니다. (latitude: -90~90, longitude: -180~180)");
        }
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0 || radiusMeters > MAX_RADIUS_METERS) {
            throw new IllegalArgumentException("radiusMeters는 0보다 크고 20000 이하여야 합니다.");
        }
        if (limit < 1 || limit > MAX_RESULT_LIMIT) {
            throw new IllegalArgumentException("limit은 1 이상 200 이하여야 합니다.");
        }
    }

    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(latDistance / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(lngDistance / 2), 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record DistanceResult(RoadImageAnalysis analysis, double distanceMeters) {
    }
}
