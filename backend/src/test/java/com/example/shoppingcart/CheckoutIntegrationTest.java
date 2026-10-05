package com.example.shoppingcart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke-level integration test for the checkout HTTP endpoint using
 * Testcontainers MongoDB.
 *
 * <p>Unlike {@code CheckoutTransactionIntegrationTest} (which targets the local
 * replica set at port 27018), this test class spins up an ephemeral MongoDB
 * container via Testcontainers. This makes it fully self-contained and suitable
 * for CI environments that have no pre-provisioned database.
 *
 * <p>Business-logic scenarios (transaction rollback, version conflict, etc.) are
 * tested in {@code CheckoutTransactionIntegrationTest} and
 * {@code CartVersionConflictIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckoutIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
            .withCommand("--replSet", "rs0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("MongoDB Testcontainer starts and has a valid replica-set URL")
    void contextLoads() {
        assertNotNull(mongoDBContainer.getReplicaSetUrl(), "Replica-set URL must not be null");
    }

    @Test
    @DisplayName("POST /api/orders/checkout returns 401 Unauthorized for unauthenticated requests")
    void checkout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", "ci-anon-key")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ci-user", roles = {"USER"})
    @DisplayName("GET /api/orders/admin/123 returns 403 Forbidden for non-admin users")
    void adminOrder_userRole_returns403() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/orders/admin/ord-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ci-user", roles = {"USER"})
    @DisplayName("POST /api/orders/checkout with authenticated USER returns 409 for empty cart (no seed data)")
    void checkout_authenticatedEmptyCart_returns409() throws Exception {
        // No products/cart seeded → cart will be empty → CartEmptyException → 409 Conflict
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", "ci-user-key-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }
}
