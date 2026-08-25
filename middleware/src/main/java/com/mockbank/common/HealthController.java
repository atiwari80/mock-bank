package com.mockbank.common;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness/readiness for anything that needs to know the app is serving.
 * <p>
 * Deliberately trivial: no {@code CustomerContext}, so no authentication, and no
 * repository, so it does not touch Postgres or either scoring service. A 200
 * here means "this process is up and handling HTTP" and nothing more — which is
 * exactly what a caller waiting to start work needs to know. Checking the
 * database here would make a healthy app look unhealthy whenever a dependency
 * blipped, and readiness would flap.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
