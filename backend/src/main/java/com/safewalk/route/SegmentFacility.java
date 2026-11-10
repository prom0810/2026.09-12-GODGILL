package com.safewalk.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 구간 점수 계산에 실제로 반영된 안전시설 스냅샷 (DB v3 segment_facility).
 * safety_facility를 FK로 참조하지 않고 종류·좌표·점수를 값으로 복사해 둔다 (원본 데이터가 바뀌어도 당시 근거가 남도록).
 *
 * - facilityType: {@link SegmentFacilityType} 이름
 * - distance(m): 구간 중간 지점에서 시설까지 거리
 * - appliedScore: 이 시설이 준 보너스. 예: CCTV는 0.15 × (1 − 거리/150)
 * 조회 전용 (저장은 {@link RouteWriter}).
 */
@Entity
@Table(name = "segment_facility")
public class SegmentFacility {

    @Id
    @Column(name = "segment_facility_id")
    private Long segmentFacilityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", nullable = false)
    private RouteSegment segment;

    @Column(name = "facility_type", nullable = false, length = 30)
    private String facilityType;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lng", nullable = false)
    private double lng;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "distance", nullable = false)
    private double distance;

    @Column(name = "applied_score", nullable = false)
    private double appliedScore;

    protected SegmentFacility() {
    }

    public Long getSegmentFacilityId() {
        return segmentFacilityId;
    }

    public RouteSegment getSegment() {
        return segment;
    }

    public String getFacilityType() {
        return facilityType;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getDistance() {
        return distance;
    }

    public double getAppliedScore() {
        return appliedScore;
    }
}
