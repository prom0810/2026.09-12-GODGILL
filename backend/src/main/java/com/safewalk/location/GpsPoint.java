package com.safewalk.location;

import java.time.Instant;

/** A phone fix; userId is taken from the JWT, never from the request body. */
public record GpsPoint(Long userId, String sessionId, double lat, double lng,
                       Instant recordedAt, Double accuracy) {
}
