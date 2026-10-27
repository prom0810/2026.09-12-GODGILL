package com.safewalk.ai.roadimage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** AI 모델이 도로 이미지 한 장을 분석한 결과. */
@Entity
@Table(name = "road_image_analysis")
public class RoadImageAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_analysis_id")
    private Long imageAnalysisId;

    @Column(name = "image_name", nullable = false, length = 255)
    private String imageName;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "sidewalk_ratio", nullable = false)
    private double sidewalkRatio;

    @Column(name = "roadway_ratio", nullable = false)
    private double roadwayRatio;

    @Column(name = "alley_ratio", nullable = false)
    private double alleyRatio;

    @Column(name = "person_count", nullable = false)
    private int personCount;

    @Column(name = "vehicle_count", nullable = false)
    private int vehicleCount;

    @Column(name = "obstacle_count", nullable = false)
    private int obstacleCount;

    @Column(name = "vehicle_sidewalk_overlap", nullable = false)
    private double vehicleSidewalkOverlap;

    @Column(name = "obstacle_sidewalk_overlap", nullable = false)
    private double obstacleSidewalkOverlap;

    @Column(name = "near_vehicle_count", nullable = false)
    private int nearVehicleCount;

    @Column(name = "near_obstacle_count", nullable = false)
    private int nearObstacleCount;

    @Column(name = "mean_vehicle_proximity", nullable = false)
    private double meanVehicleProximity;

    @Column(name = "mean_obstacle_proximity", nullable = false)
    private double meanObstacleProximity;

    @Column(name = "scene_openness", nullable = false)
    private double sceneOpenness;

    @Column(name = "image_risk", nullable = false)
    private double imageRisk;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "analyzed_at", nullable = false)
    private OffsetDateTime analyzedAt;

    /** JPA 전용 기본 생성자 */
    protected RoadImageAnalysis() {
    }

    public Long getImageAnalysisId() {
        return imageAnalysisId;
    }

    public String getImageName() {
        return imageName;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getDirection() {
        return direction;
    }

    public double getSidewalkRatio() {
        return sidewalkRatio;
    }

    public double getRoadwayRatio() {
        return roadwayRatio;
    }

    public double getAlleyRatio() {
        return alleyRatio;
    }

    public int getPersonCount() {
        return personCount;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public int getObstacleCount() {
        return obstacleCount;
    }

    public double getVehicleSidewalkOverlap() {
        return vehicleSidewalkOverlap;
    }

    public double getObstacleSidewalkOverlap() {
        return obstacleSidewalkOverlap;
    }

    public int getNearVehicleCount() {
        return nearVehicleCount;
    }

    public int getNearObstacleCount() {
        return nearObstacleCount;
    }

    public double getMeanVehicleProximity() {
        return meanVehicleProximity;
    }

    public double getMeanObstacleProximity() {
        return meanObstacleProximity;
    }

    public double getSceneOpenness() {
        return sceneOpenness;
    }

    public double getImageRisk() {
        return imageRisk;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public OffsetDateTime getAnalyzedAt() {
        return analyzedAt;
    }
}
