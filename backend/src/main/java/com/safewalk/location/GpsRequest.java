package com.safewalk.location;

import java.time.Instant;

public record GpsRequest(Double lat, Double lng, Instant recordedAt, Double accuracy) {
}
