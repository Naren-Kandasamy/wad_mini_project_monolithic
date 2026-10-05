package com.example.shoppingcart.security;

import com.example.shoppingcart.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full Spring Security integration tests verifying:
 * - Route-level RBAC enforcement
 * - JWT-derived identity (IDOR prevention)
 * - Forged identity header rejection
 * - CORS pre-flight
 * - RFC 7807 ProblemDetail error format
 *
 * Dummy controllers are explicitly imported via @Import so they register
 * as beans in the test application context (test-source classes are not
 * component-scanned by @SpringBootTest).
 *
 * JwtDecoder is replaced with a @MockBean to prevent the application
 * from making a live JWKS call to Keycloak during tests.
 */
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"})
@AutoConfigureMockMvc
@Import({
        SecurityIntegrationTest.DummyProductController.class,
        SecurityIntegrationTest.DummyCartController.class,
        SecurityIntegrationTest.DummyOrderController.class,
        SecurityIntegrationTest.DummyAdminController.class
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private JwtDecoder jwtDecoder;

    // ─── Dummy controllers (registered via @Import above) ──────────────────────

    @RestController
    @RequestMapping("/api/products")
    static class DummyProductController {
        @GetMapping
        public List<String> listProducts() {
            return List.of("Product1", "Product2");
        }

        @PostMapping
        public Map<String, String> createProduct() {
            return Map.of("status", "created");
        }
    }

    @RestController
    @RequestMapping("/api/carts/me")
    static class DummyCartController {
        @GetMapping
        public Map<String, Object> getMyCart() {
            // Derives identity strictly from validated JWT subject via SecurityUtils
            return Map.of("userId", SecurityUtils.getAuthenticatedUserId());
        }
    }

    @RestController
    @RequestMapping("/api/orders")
    static class DummyOrderController {
        @GetMapping("/{id}")
        public Map<String, Object> getOrder(@PathVariable String id) {
            String currentUserId = SecurityUtils.getAuthenticatedUserId();
            // order-1 belongs to user-alice only; non-admin accessing it returns 404 (IDOR prevention)
            if ("order-1".equals(id) && !"user-alice".equals(currentUserId) && !SecurityUtils.hasRole("ADMIN")) {
                throw new ResourceNotFoundException("Order not found: " + id);
            }
            return Map.of("orderId", id, "owner", "user-alice");
        }
    }

    @RestController
    @RequestMapping("/api/admin")
    static class DummyAdminController {
        @GetMapping("/stats")
        public Map<String, String> getStats() {
            return Map.of("system", "ok");
        }
    }

    // ─── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products is publicly accessible without any token")
    void getProductsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/products returns 401 Unauthorized when no token supplied")
    void postProductUnauthenticatedShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/products returns 403 Forbidden for USER role")
    void postProductAsUserShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/products returns 200 OK for ADMIN role")
    void postProductAsAdminShouldReturn200() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/carts/me returns 401 Unauthorized when unauthenticated")
    void getCartMeUnauthenticatedShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/carts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/carts/me uses Jwt.sub as canonical identity — ignores forged X-User-Id header")
    void getCartMeShouldDeriveIdentityFromJwtSubjectIgnoringForgedHeader() throws Exception {
        mockMvc.perform(get("/api/carts/me")
                        .with(jwt().jwt(j -> j.subject("real-user-123"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .header("X-User-Id", "forged-attacker-id")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("real-user-123"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} returns 404 Not Found when non-owner non-admin accesses another user's order — IDOR prevention")
    void getOrderForeignUserShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/orders/order-1")
                        .with(jwt().jwt(j -> j.subject("user-bob"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} succeeds when accessed by the order owner")
    void getOrderOwnerShouldSucceed() throws Exception {
        mockMvc.perform(get("/api/orders/order-1")
                        .with(jwt().jwt(j -> j.subject("user-alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"));
    }

    @Test
    @DisplayName("GET /api/admin/stats returns 403 Forbidden for USER role")
    void getAdminStatsAsUserShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/admin/stats")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/stats returns 200 OK for ADMIN role")
    void getAdminStatsAsAdminShouldReturn200() throws Exception {
        mockMvc.perform(get("/api/admin/stats")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OPTIONS preflight from allowed origin returns CORS headers")
    void corsPreflightFromAllowedOriginShouldSucceed() throws Exception {
        mockMvc.perform(options("/api/products")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("Unauthenticated request to protected /api/orders returns RFC 7807 Unauthorized body")
    void unauthenticatedRequestReturnsProblemDetailBody() throws Exception {
        mockMvc.perform(get("/api/orders/some-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/problem+json")));
    }
}
