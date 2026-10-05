package com.example.shoppingcart.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Administrative controller providing system-level diagnostics and statistics.
 *
 * <p>Protected strictly by Spring Security with {@code hasRole('ADMIN')}.
 * Resolves the unwired {@code /api/admin/**} rule declared in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "system", "ok",
                "status", "UP",
                "serverTime", Instant.now().toString(),
                "node", "monolith-instance-1"
        ));
    }
}
