package com.safewalk.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeRouteServiceTest {
    private final SafeRouteService service = new SafeRouteService();

    @Test
    void safetyWeightCanSelectALongerSaferRoute() {
        RouteGraph graph = new RouteGraph(
            Map.of(
                "start", new Node("start", 36.8100, 127.1467),
                "short", new Node("short", 36.8110, 127.1467),
                "safe", new Node("safe", 36.8100, 127.1480),
                "goal", new Node("goal", 36.8110, 127.1480)
            ),
            Map.of(
                "start", List.of(new Edge("short", 100, 0.1), new Edge("safe", 120, 1.0)),
                "short", List.of(new Edge("goal", 100, 0.1)),
                "safe", List.of(new Edge("goal", 120, 1.0))
            )
        );

        SafeRouteService.Result shortest = service.findRoute(graph, "start", "goal", 0.0);
        SafeRouteService.Result safest = service.findRoute(graph, "start", "goal", 0.55);

        assertEquals("short", shortest.segments().getFirst().to().id());
        assertEquals(200, shortest.totalDistanceMeters());
        assertEquals("safe", safest.segments().getFirst().to().id());
        assertEquals(240, safest.totalDistanceMeters());
        assertEquals(1.0, safest.averageSafetyScore());
    }
}
