package com.mockbank.creditcheck;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Readiness for this service. Answers 200 once it is handling HTTP, and reveals
 * nothing about how a bureau report is derived.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
