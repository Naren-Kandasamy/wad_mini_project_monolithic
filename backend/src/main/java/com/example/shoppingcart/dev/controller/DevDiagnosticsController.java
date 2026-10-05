package com.example.shoppingcart.dev.controller;

import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

/**
 * Developer diagnostic endpoint providing runtime environment information.
 *
 * <p>Protected strictly by Spring Security with {@code hasRole('DEVELOPER')}.
 * Resolves the unwired {@code /api/dev/**} rule declared in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/dev")
public class DevDiagnosticsController {

    private final Environment environment;

    public DevDiagnosticsController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getDevInfo() {
        return ResponseEntity.ok(Map.of(
                "application", "shopping-cart-monolith",
                "activeProfiles", Arrays.asList(environment.getActiveProfiles()),
                "javaVersion", System.getProperty("java.version"),
                "status", "DEBUG_READY"
        ));
    }
}
