package com.safewalk.route;

/** 두 좌표 사이 거리(m). 앱의 RouteSafetyScorer.haversineMeters와 같은 식이다. */
public final class GeoDistance {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoDistance() {
    }

    public static double meters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
