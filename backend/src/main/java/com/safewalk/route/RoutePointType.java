package com.safewalk.route;

/** route_point.point_type. 첫 지점은 START, 마지막은 END, 나머지는 WAYPOINT (서버가 순서로 정한다) */
public enum RoutePointType {
    START,
    WAYPOINT,
    END
}
