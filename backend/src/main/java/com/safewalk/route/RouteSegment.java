package com.safewalk.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 연속된 두 지점 사이 구간 (DB v3 route_segment). 앱 A*의 간선(edge) 하나와 1:1이다.
 *
 * - distance(m): 서버가 두 지점 좌표로 계산
 * - safetyScore(0~1): 앱이 A*에 실제로 쓴 정규화 안전점수. 불러온 지도 범위에 따라 달라지는 상대값이다
 * - riskScore(0 이상): 위험구역 감점의 합(절대값). 위험구역 밖이면 0
 *
 * route_id 컬럼이 없어서 어느 경로의 구간인지는 시작 지점(startPoint.route)으로 찾는다.
 * 조회 전용 (저장은 {@link RouteWriter}).
 */
@Entity
@Table(name = "route_segment")
public class RouteSegment {

    @Id
    @Column(name = "segment_id")
    private Long segmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_point_id", nullable = false)
    private RoutePoint startPoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_point_id", nullable = false)
    private RoutePoint endPoint;

    @Column(name = "distance", nullable = false)
    private double distance;

    @Column(name = "safety_score", nullable = false)
    private double safetyScore;

    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    protected RouteSegment() {
    }

    public Long getSegmentId() {
        return segmentId;
    }

    public RoutePoint getStartPoint() {
        return startPoint;
    }

    public RoutePoint getEndPoint() {
        return endPoint;
    }

    public double getDistance() {
        return distance;
    }

    public double getSafetyScore() {
        return safetyScore;
    }

    public double getRiskScore() {
        return riskScore;
    }
}
