package com.example.shoppingcart.cart.controller;

import com.example.shoppingcart.cart.dto.CartItemResponse;
import com.example.shoppingcart.cart.dto.CartResponse;
import com.example.shoppingcart.cart.service.CartService;
import com.example.shoppingcart.shared.error.GlobalExceptionHandler;
import com.example.shoppingcart.shared.security.SecurityContextPrincipalResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private SecurityContextPrincipalResolver principalResolver;

    @Test
    @DisplayName("GET /api/carts/me returns current user cart")
    void getCart_returnsCart() throws Exception {
        when(principalResolver.getCurrentUserId()).thenReturn("user1");
        CartResponse cart = new CartResponse("c1", "user1", 1L, List.of(), BigDecimal.ZERO, Instant.now());
        when(cartService.getCart("user1")).thenReturn(cart);

        mockMvc.perform(get("/api/carts/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("user1"))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("POST /api/carts/me/items adds item and returns updated cart")
    void addItem_returnsUpdatedCart() throws Exception {
        when(principalResolver.getCurrentUserId()).thenReturn("user1");
        CartItemResponse item = new CartItemResponse("p1", "Item", new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        CartResponse cart = new CartResponse("c1", "user1", 2L, List.of(item), new BigDecimal("20.00"), Instant.now());

        when(cartService.addItem(eq("user1"), any())).thenReturn(cart);

        String json = """
            {
                "productId": "p1",
                "quantity": 2
            }
            """;

        mockMvc.perform(post("/api/carts/me/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].productId").value("p1"))
            .andExpect(jsonPath("$.subtotal").value(20.00));
    }

    @Test
    @DisplayName("DELETE /api/carts/me clears cart and returns 204 No Content")
    void clearCart_returnsNoContent() throws Exception {
        when(principalResolver.getCurrentUserId()).thenReturn("user1");

        mockMvc.perform(delete("/api/carts/me"))
            .andExpect(status().isNoContent());
    }
}
