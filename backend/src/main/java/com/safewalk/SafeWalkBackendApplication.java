package com.safewalk;

import com.safewalk.route.SafeRouteService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SafeWalkBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafeWalkBackendApplication.class, args);
    }

    @Bean
    SafeRouteService safeRouteService() {
        return new SafeRouteService();
    }
}
