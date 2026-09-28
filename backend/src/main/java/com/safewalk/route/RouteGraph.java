package com.safewalk.route;

import java.util.List;
import java.util.Map;

/** Immutable snapshot of a pedestrian road network. */
public record RouteGraph(Map<String, Node> nodes, Map<String, List<Edge>> adjacency) {
    public RouteGraph {
        nodes = Map.copyOf(nodes);
        adjacency = adjacency.entrySet().stream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))
        );
    }
}
