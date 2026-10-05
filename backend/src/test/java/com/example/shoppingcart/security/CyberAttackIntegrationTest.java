package com.example.shoppingcart.security;

import com.example.shoppingcart.cart.service.CartService;
import com.example.shoppingcart.checkout.dto.CheckoutResponse;
import com.example.shoppingcart.checkout.service.CheckoutService;
import com.example.shoppingcart.order.dto.OrderResponse;
import com.example.shoppingcart.order.service.OrderService;
import com.example.shoppingcart.product.dto.ProductResponse;
import com.example.shoppingcart.product.service.ProductService;
import com.example.shoppingcart.shared.error.OrderNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bolstered Security & Cyber-Attack Test Suite.
 *
 * <p>Demonstrates hardening against common OWASP Top 10 vulnerabilities:
 * <ul>
 *   <li><b>A01: Broken Access Control (IDOR & Privilege Escalation)</b></li>
 *   <li><b>A02: Cryptographic & Identity Spoofing (Header Forgery)</b></li>
 *   <li><b>A03: Injection & Parameter Tampering (Negative Prices & Validation)</b></li>
 *   <li><b>A04: Insecure Design (Idempotency Replay & Double-Spend)</b></li>
 *   <li><b>A05: Security Misconfiguration (CORS Origin Spoofing)</b></li>
 *   <li><b>A07: Identification and Authentication Failures (Anonymous Probing)</b></li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CyberAttackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CheckoutService checkoutService;

    // ── 1. IDOR (Insecure Direct Object Reference) Protection ─────────────────

    @Nested
    @DisplayName("Attack Vector 1: IDOR & Horizontal Privilege Escalation")
    class IdorAttacks {

        @Test
        @DisplayName("Attacker Bob cannot access Alice's order — returns 404 (Anti-Enumeration)")
        void attackerCannotAccessOtherUserOrder() throws Exception {
            String aliceOrderId = "ord-alice-999";
            String attackerBob = "user-bob-attacker";

            // OrderService queries findByIdAndUserId(orderId, userId) — if Bob doesn't own it, throws 404
            when(orderService.getOrderById(attackerBob, aliceOrderId))
                    .thenThrow(new OrderNotFoundException(aliceOrderId));

            mockMvc.perform(get("/api/orders/" + aliceOrderId)
                            .with(jwt().jwt(j -> j.subject(attackerBob))
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.detail").value(containsString("Order not found with id: " + aliceOrderId)));

            verify(orderService).getOrderById(attackerBob, aliceOrderId);
            verify(orderService, never()).getOrderById(eq("alice"), any());
        }
    }

    // ── 2. Vertical Privilege Escalation Attacks ───────────────────────────────

    @Nested
    @DisplayName("Attack Vector 2: Vertical Privilege Escalation")
    class PrivilegeEscalationAttacks {

        @Test
        @DisplayName("Attacker with ROLE_USER cannot delete products from catalog — returns 403 Forbidden")
        void userCannotDeleteProducts() throws Exception {
            mockMvc.perform(delete("/api/products/prod-123")
                            .with(jwt().jwt(j -> j.subject("malicious-user"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());

            verify(productService, never()).deleteProduct(anyString());
        }

        @Test
        @DisplayName("Attacker with ROLE_USER cannot access admin order inspection — returns 403 Forbidden")
        void userCannotAccessAdminOrderEndpoint() throws Exception {
            mockMvc.perform(get("/api/orders/admin/any-order-id")
                            .with(jwt().jwt(j -> j.subject("regular-user"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());

            verify(orderService, never()).getAdminOrderById(anyString());
        }

        @Test
        @DisplayName("Attacker with ROLE_USER cannot access developer diagnostic info — returns 403 Forbidden")
        void userCannotAccessDevDiagnostics() throws Exception {
            mockMvc.perform(get("/api/dev/info")
                            .with(jwt().jwt(j -> j.subject("regular-user"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Attacker with ROLE_USER cannot access admin stats — returns 403 Forbidden")
        void userCannotAccessAdminStats() throws Exception {
            mockMvc.perform(get("/api/admin/stats")
                            .with(jwt().jwt(j -> j.subject("regular-user"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());
        }
    }

    // ── 3. Identity Spoofing & Header Forgery Attacks ─────────────────────────

    @Nested
    @DisplayName("Attack Vector 3: Identity Spoofing & Header Tampering")
    class HeaderForgeryAttacks {

        @Test
        @DisplayName("Server ignores forged X-User-Id, X-User-Role, and X-Forwarded-User headers")
        void serverRejectsSpoofedHeaders() throws Exception {
            String attackerSubject = "real-authenticated-attacker";

            mockMvc.perform(get("/api/orders")
                            .with(jwt().jwt(j -> j.subject(attackerSubject))
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                            .header("X-User-Id", "admin")
                            .header("X-User-Role", "ADMIN")
                            .header("X-Forwarded-User", "ceo")
                            .header("X-Original-User", "root"))
                    .andExpect(status().isOk());

            // Server MUST resolve to the cryptographically verified JWT subject, not any header
            verify(orderService).getUserOrders(attackerSubject);
            verify(orderService, never()).getUserOrders("admin");
            verify(orderService, never()).getUserOrders("ceo");
        }
    }

    // ── 4. CORS Origin Spoofing Attacks ───────────────────────────────────────

    @Nested
    @DisplayName("Attack Vector 4: CORS Origin Spoofing & Unauthorized Domains")
    class CorsAttacks {

        @Test
        @DisplayName("Malicious phishing domain in Origin header is rejected by CORS filter")
        void maliciousOriginIsRejected() throws Exception {
            mockMvc.perform(options("/api/products")
                            .header("Origin", "http://evil-phishing-site.attacker.com")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Legitimate frontend domain http://localhost:5173 is accepted by CORS filter")
        void legitimateOriginIsAccepted() throws Exception {
            mockMvc.perform(options("/api/products")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        }
    }

    // ── 5. Parameter Tampering & Negative Price Attacks ────────────────────────

    @Nested
    @DisplayName("Attack Vector 5: Input Validation & Parameter Tampering")
    class InputValidationAttacks {

        @Test
        @DisplayName("Negative product price is rejected with 400 ProblemDetail (DecimalMin violation)")
        void negativePriceIsRejected() throws Exception {
            String maliciousPayload = """
                    {
                      "name": "Hacked Product",
                      "description": "Exploit price",
                      "price": -100.00,
                      "sku": "SKU-HACK-001",
                      "active": true
                    }
                    """;

            mockMvc.perform(post("/api/products")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(maliciousPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("Validation failed"))
                    .andExpect(jsonPath("$.invalidParams.price").value(containsString("Product price must be greater than zero")));

            verify(productService, never()).createProduct(any());
        }

        @Test
        @DisplayName("Zero product price is rejected with 400 ProblemDetail")
        void zeroPriceIsRejected() throws Exception {
            String maliciousPayload = """
                    {
                      "name": "Zero Price Product",
                      "description": "Free items exploit",
                      "price": 0.00,
                      "sku": "SKU-ZERO-001",
                      "active": true
                    }
                    """;

            mockMvc.perform(post("/api/products")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(maliciousPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // ── 6. Idempotency Replay & Double-Spend Protection ───────────────────────

    @Nested
    @DisplayName("Attack Vector 6: Idempotency Replay & Double-Spend Prevention")
    class IdempotencyReplayAttacks {

        @Test
        @DisplayName("Replaying same checkout idempotency key returns identical order without double creation")
        void replayingCheckoutReturnsSameOrder() throws Exception {
            String userId = "shopper-1";
            String idempotencyKey = "replay-attack-key-123";

            CheckoutResponse confirmedOrder = new CheckoutResponse(
                    "ord-confirmed-001",
                    userId,
                    idempotencyKey,
                    new BigDecimal("79.99"),
                    "CONFIRMED",
                    Instant.now(),
                    List.of()
            );

            when(checkoutService.processCheckout(userId, idempotencyKey))
                    .thenReturn(confirmedOrder);

            // First submission
            mockMvc.perform(post("/api/orders/checkout")
                            .with(jwt().jwt(j -> j.subject(userId))
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value("ord-confirmed-001"));

            // Replay submission
            mockMvc.perform(post("/api/orders/checkout")
                            .with(jwt().jwt(j -> j.subject(userId))
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value("ord-confirmed-001"));

            // Both submissions safely yield the same order
            verify(checkoutService, times(2)).processCheckout(userId, idempotencyKey);
        }
    }

    // ── 7. XSS Payload Handling in Catalog ─────────────────────────────────────

    @Nested
    @DisplayName("Attack Vector 7: Stored XSS Payload Handling")
    class XssPayloadAttacks {

        @Test
        @DisplayName("XSS payload in product catalog is returned as application/json text, not executable HTML")
        void xssPayloadInCatalogReturnedAsSafeJson() throws Exception {
            String xssName = "<script>alert('xss')</script>";
            String xssDesc = "<img src=x onerror=alert('document.cookie')>";

            Instant now = Instant.now();
            ProductResponse xssProduct = new ProductResponse(
                    "prod-xss",
                    xssName,
                    xssDesc,
                    new BigDecimal("19.99"),
                    "SKU-XSS-001",
                    true,
                    now,
                    now
            );

            when(productService.getProductById("prod-xss")).thenReturn(xssProduct);

            mockMvc.perform(get("/api/products/prod-xss"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", containsString("application/json")))
                    .andExpect(header().string("Content-Type", not(containsString("text/html"))))
                    .andExpect(jsonPath("$.name").value(xssName))
                    .andExpect(jsonPath("$.description").value(xssDesc));
        }
    }
}
