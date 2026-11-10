package com.safewalk.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 경로의 지점 (DB v3 route_point). seq 0부터 순서대로, 첫 지점 START · 마지막 END · 나머지 WAYPOINT.
 * WAYPOINT는 앱 A* 경로의 도로 노드 하나하나다. name·address는 START/END에만 있다.
 * 조회 전용 (저장은 {@link RouteWriter}).
 */
@Entity
@Table(name = "route_point")
public class RoutePoint {

    @Id
    @Column(name = "point_id")
    private Long pointId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "point_type", nullable = false, length = 20)
    private RoutePointType pointType;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lng", nullable = false)
    private double lng;

    protected RoutePoint() {
    }

    public Long getPointId() {
        return pointId;
    }

    public Route getRoute() {
        return route;
    }

    public int getSeq() {
        return seq;
    }

    public RoutePointType getPointType() {
        return pointType;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }
}
