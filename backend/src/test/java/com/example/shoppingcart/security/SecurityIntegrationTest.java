package com.example.shoppingcart.security;

import com.example.shoppingcart.cart.service.CartService;
import com.example.shoppingcart.checkout.service.CheckoutService;
import com.example.shoppingcart.order.service.OrderService;
import com.example.shoppingcart.product.service.ProductService;
import com.example.shoppingcart.shared.error.OrderNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack security integration tests against the REAL Track 1 controllers
 * (domain services mocked) plus the real SecurityConfig filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CheckoutService checkoutService;

    @Test
    @DisplayName("GET /api/products is publicly accessible without token")
    void getProductsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/products returns 401 Unauthorized when unauthenticated")
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
    @DisplayName("POST /api/products passes security for ADMIN (empty body then fails validation with 400, not 401/403)")
    void postProductAsAdminShouldPassSecurity() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/carts/me returns 401 Unauthorized when unauthenticated")
    void getCartMeUnauthenticatedShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/carts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/carts/me derives userId strictly from Jwt.sub and ignores forged X-User-Id")
    void getCartMeShouldDeriveIdentityFromJwtSubject() throws Exception {
        mockMvc.perform(get("/api/carts/me")
                        .with(jwt().jwt(j -> j.subject("real-user-123"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .header("X-User-Id", "forged-attacker-id")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        verify(cartService).getCart("real-user-123");
        verify(cartService, never()).getCart("forged-attacker-id");
    }

    @Test
    @DisplayName("GET /api/orders/{id} returns 404 Not Found when another user's order is requested (IDOR protection)")
    void getOrderForeignUserShouldReturn404() throws Exception {
        when(orderService.getOrderById("user-bob", "order-1"))
                .thenThrow(new OrderNotFoundException("order-1"));

        mockMvc.perform(get("/api/orders/order-1")
                        .with(jwt().jwt(j -> j.subject("user-bob"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} resolves the lookup with the caller's own Jwt.sub")
    void getOrderOwnerShouldSucceed() throws Exception {
        mockMvc.perform(get("/api/orders/order-1")
                        .with(jwt().jwt(j -> j.subject("user-alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());

        verify(orderService).getOrderById("user-alice", "order-1");
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
    @DisplayName("OPTIONS preflight requests handle CORS correctly")
    void corsOptionsRequestShouldBeAllowed() throws Exception {
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
