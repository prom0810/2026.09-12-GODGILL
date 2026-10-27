package com.safewalk.roadimage;

import java.time.OffsetDateTime;

public record RoadImageAnalysisResponse(
        Long imageAnalysisId,
        String imageName,
        double latitude,
        double longitude,
        String direction,
        double sidewalkRatio,
        double roadwayRatio,
        double alleyRatio,
        int personCount,
        int vehicleCount,
        int obstacleCount,
        double vehicleSidewalkOverlap,
        double obstacleSidewalkOverlap,
        int nearVehicleCount,
        int nearObstacleCount,
        double meanVehicleProximity,
        double meanObstacleProximity,
        double sceneOpenness,
        double imageRisk,
        String riskLevel,
        String modelVersion,
        OffsetDateTime analyzedAt
) {

    public static RoadImageAnalysisResponse from(RoadImageAnalysis analysis) {
        return new RoadImageAnalysisResponse(
                analysis.getImageAnalysisId(),
                analysis.getImageName(),
                analysis.getLatitude(),
                analysis.getLongitude(),
                analysis.getDirection(),
                analysis.getSidewalkRatio(),
                analysis.getRoadwayRatio(),
                analysis.getAlleyRatio(),
                analysis.getPersonCount(),
                analysis.getVehicleCount(),
                analysis.getObstacleCount(),
                analysis.getVehicleSidewalkOverlap(),
                analysis.getObstacleSidewalkOverlap(),
                analysis.getNearVehicleCount(),
                analysis.getNearObstacleCount(),
                analysis.getMeanVehicleProximity(),
                analysis.getMeanObstacleProximity(),
                analysis.getSceneOpenness(),
                analysis.getImageRisk(),
                analysis.getRiskLevel(),
                analysis.getModelVersion(),
                analysis.getAnalyzedAt()
        );
    }
}
