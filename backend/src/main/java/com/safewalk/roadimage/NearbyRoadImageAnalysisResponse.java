package com.safewalk.roadimage;

/** 요청 좌표에서 가까운 이미지 분석 결과와 직선거리(미터). */
public record NearbyRoadImageAnalysisResponse(
        RoadImageAnalysisResponse analysis,
        double distanceMeters
) {
}
