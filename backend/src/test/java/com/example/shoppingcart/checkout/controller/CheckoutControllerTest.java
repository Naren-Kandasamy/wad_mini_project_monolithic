package com.example.shoppingcart.checkout.controller;

import com.example.shoppingcart.checkout.dto.CheckoutResponse;
import com.example.shoppingcart.checkout.service.CheckoutService;
import com.example.shoppingcart.shared.error.CartEmptyException;
import com.example.shoppingcart.shared.error.CartVersionConflictException;
import com.example.shoppingcart.shared.error.GlobalExceptionHandler;
import com.example.shoppingcart.shared.security.SecurityContextPrincipalResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CheckoutController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutService checkoutService;

    @MockitoBean
    private SecurityContextPrincipalResolver principalResolver;

    @Test
    @DisplayName("POST /api/orders/checkout returns 201 Created and order details")
    void checkout_returnsCreated() throws Exception {
        when(principalResolver.getCurrentUserId()).thenReturn("user1");
        CheckoutResponse resp = new CheckoutResponse("ord-1", "user1", "idem-key", new BigDecimal("50.00"), "CONFIRMED", Instant.now(), List.of());
        when(checkoutService.processCheckout("user1", "idem-key")).thenReturn(resp);

        mockMvc.perform(post("/api/orders/checkout")
                .header("Idempotency-Key", "idem-key"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value("ord-1"))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.totalAmount").value(50.00));
    }

    @Test
    @DisplayName("POST /api/orders/checkout returns 409 Conflict when cart is empty")
    void checkout_emptyCart_returnsConflict() throws Exception {
        when(principalResolver.getCurrentUserId()).thenReturn("user1");
        when(checkoutService.processCheckout(eq("user1"), anyString()))
            .thenThrow(new CartEmptyException("Cannot checkout with an empty cart"));

        mockMvc.perform(post("/api/orders/checkout")
                .header("Idempotency-Key", "idem-empty"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /api/orders/checkout returns 409 Conflict when version conflicts")
    void checkout_versionConflict_returnsConflict() throws Exception {
        when(principalResolver.getCurrentUserId()).thenReturn("user1");
        when(checkoutService.processCheckout(eq("user1"), anyString()))
            .thenThrow(new CartVersionConflictException("Cart version conflict"));

        mockMvc.perform(post("/api/orders/checkout")
                .header("Idempotency-Key", "idem-conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }
}
