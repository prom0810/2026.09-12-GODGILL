package com.safewalk.route;

/** A geographic node in the pedestrian road graph. */
public record Node(String id, double latitude, double longitude) {
    public Node {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Node id must not be blank");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid latitude or longitude");
        }
    }
}
