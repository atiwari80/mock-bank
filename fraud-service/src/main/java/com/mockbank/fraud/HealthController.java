package com.mockbank.fraud;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Readiness for this service. Answers 200 once it is handling HTTP, and reveals
 * nothing about scoring — the black box stays closed.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
