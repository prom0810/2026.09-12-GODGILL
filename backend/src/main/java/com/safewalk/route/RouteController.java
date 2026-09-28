package com.safewalk.route;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final SafeRouteService routeService;

    public RouteController(SafeRouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/calculate")
    public SafeRouteService.Result calculate(@RequestBody RouteRequest request) {
        SafeRouteService.Result result = routeService.findRoute(
            request.graph(),
            request.startNodeId(),
            request.goalNodeId(),
            request.safetyWeight()
        );
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No route was found");
        }
        return result;
    }

    public record RouteRequest(
        RouteGraph graph,
        String startNodeId,
        String goalNodeId,
        double safetyWeight
    ) {}
}
