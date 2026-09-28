package com.safewalk.route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Calculates shortest and safety-weighted walking routes with A*. */
public final class SafeRouteService {
    private static final double SAFETY_PENALTY_FACTOR = 6.0;

    public Result findRoute(RouteGraph graph, String startId, String goalId, double safetyWeight) {
        if (!Double.isFinite(safetyWeight) || safetyWeight < 0 || safetyWeight > 1) {
            throw new IllegalArgumentException("Safety weight must be between 0 and 1");
        }
        Node start = graph.nodes().get(startId);
        Node goal = graph.nodes().get(goalId);
        if (start == null || goal == null) return null;

        Map<String, Double> costs = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        Map<String, Edge> previousEdges = new HashMap<>();
        Set<String> visited = new HashSet<>();
        PriorityQueue<Candidate> open = new PriorityQueue<>(Comparator.comparingDouble(Candidate::estimatedCost));
        costs.put(startId, 0.0);
        open.add(new Candidate(startId, distance(start, goal)));

        while (!open.isEmpty()) {
            String currentId = open.remove().nodeId();
            if (currentId.equals(goalId)) {
                return buildResult(graph.nodes(), previous, previousEdges, startId, goalId);
            }
            if (!visited.add(currentId)) continue;

            double currentCost = costs.getOrDefault(currentId, Double.POSITIVE_INFINITY);
            for (Edge edge : graph.adjacency().getOrDefault(currentId, List.of())) {
                if (visited.contains(edge.toNodeId())) continue;
                Node destination = graph.nodes().get(edge.toNodeId());
                if (destination == null) continue;

                double edgeCost = edge.distanceMeters()
                    * (1 + safetyWeight * (1 - edge.safetyScore()) * SAFETY_PENALTY_FACTOR);
                double nextCost = currentCost + edgeCost;
                if (nextCost < costs.getOrDefault(edge.toNodeId(), Double.POSITIVE_INFINITY)) {
                    costs.put(edge.toNodeId(), nextCost);
                    previous.put(edge.toNodeId(), currentId);
                    previousEdges.put(edge.toNodeId(), edge);
                    open.add(new Candidate(edge.toNodeId(), nextCost + distance(destination, goal)));
                }
            }
        }
        return null;
    }

    private Result buildResult(Map<String, Node> nodes, Map<String, String> previous,
                               Map<String, Edge> previousEdges, String startId, String goalId) {
        ArrayDeque<String> path = new ArrayDeque<>();
        String current = goalId;
        path.addFirst(current);
        while (!current.equals(startId)) {
            current = previous.get(current);
            if (current == null) return null;
            path.addFirst(current);
        }

        List<Segment> segments = new ArrayList<>();
        double distance = 0;
        double safety = 0;
        String fromId = path.removeFirst();
        while (!path.isEmpty()) {
            String toId = path.removeFirst();
            Edge edge = previousEdges.get(toId);
            Segment segment = new Segment(nodes.get(fromId), nodes.get(toId), edge.distanceMeters(), edge.safetyScore());
            segments.add(segment);
            distance += edge.distanceMeters();
            safety += edge.safetyScore();
            fromId = toId;
        }
        return new Result(segments, distance, segments.isEmpty() ? 1.0 : safety / segments.size());
    }

    private double distance(Node a, Node b) {
        double earthRadius = 6_371_000.0;
        double latitude = Math.toRadians(b.latitude() - a.latitude());
        double longitude = Math.toRadians(b.longitude() - a.longitude());
        double value = Math.pow(Math.sin(latitude / 2), 2)
            + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude()))
            * Math.pow(Math.sin(longitude / 2), 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private record Candidate(String nodeId, double estimatedCost) {}

    public record Segment(Node from, Node to, double distanceMeters, double safetyScore) {}

    public record Result(List<Segment> segments, double totalDistanceMeters, double averageSafetyScore) {
        public Result {
            segments = List.copyOf(segments);
        }
    }
}
