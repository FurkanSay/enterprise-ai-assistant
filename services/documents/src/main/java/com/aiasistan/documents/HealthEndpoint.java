package com.aiasistan.documents;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Custom /health/live and /health/ready endpoints kept in addition to the
 * Spring Boot Actuator probes so the docker-compose healthcheck matches the
 * same path shape used by every other service in the platform.
 *
 * Real readiness checks (DB, MinIO, Redis) are added in Phase D.
 */
@RestController
public class HealthEndpoint {

    @GetMapping("/health/live")
    public Map<String, String> live() {
        return Map.of("status", "ok", "service", "documents");
    }

    @GetMapping("/health/ready")
    public Map<String, String> ready() {
        return Map.of("status", "ok", "service", "documents");
    }
}
