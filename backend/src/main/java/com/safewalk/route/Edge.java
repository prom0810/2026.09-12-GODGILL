package com.safewalk.route;

/** A directed road segment and its normalized safety score (0.0 to 1.0). */
public record Edge(String toNodeId, double distanceMeters, double safetyScore) {
    public Edge {
        if (toNodeId == null || toNodeId.isBlank()) {
            throw new IllegalArgumentException("Destination node id must not be blank");
        }
        if (!Double.isFinite(distanceMeters) || distanceMeters <= 0) {
            throw new IllegalArgumentException("Distance must be positive");
        }
        if (!Double.isFinite(safetyScore) || safetyScore < 0 || safetyScore > 1) {
            throw new IllegalArgumentException("Safety score must be between 0 and 1");
        }
    }
}
