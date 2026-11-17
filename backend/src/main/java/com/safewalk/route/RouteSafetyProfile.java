package com.safewalk.route;

import java.util.Map;

public record RouteSafetyProfile(RouteAgeBand ageBand, boolean birthDateRequired,
                                 Map<String, Double> facilityWeights) {
}
