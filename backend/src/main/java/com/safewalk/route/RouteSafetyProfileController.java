package com.safewalk.route;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RouteSafetyProfileController {
    private final RouteSafetyProfileService profiles;

    public RouteSafetyProfileController(RouteSafetyProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/routes/safety-profile")
    public ApiResponse<RouteSafetyProfile> get(@LoginUser Long userId) {
        return ApiResponse.ok(profiles.get(userId));
    }
}
