package com.example.shoppingcart.order.controller;

import com.example.shoppingcart.order.dto.OrderResponse;
import com.example.shoppingcart.order.service.OrderService;
import com.example.shoppingcart.shared.error.GlobalExceptionHandler;
import com.example.shoppingcart.shared.error.OrderNotFoundException;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private SecurityContextPrincipalResolver principalResolver;

    @Test
    @DisplayName("GET /api/orders returns user's orders")
    void getUserOrders_returnsOrders() throws Exception {
        when(principalResolver.getCurrentUserId()).thenReturn("user1");
        OrderResponse order = new OrderResponse("ord-1", "user1", "idem-1", List.of(), new BigDecimal("45.00"), "CONFIRMED", Instant.now());
        when(orderService.getUserOrders("user1")).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("ord-1"))
            .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} returns 404 when order does not belong to user or missing")
    void getOrderById_returnsNotFound() throws Exception {
        when(principalResolver.getCurrentUserId()).thenReturn("user1");
        when(orderService.getOrderById("user1", "missing")).thenThrow(new OrderNotFoundException("missing"));

        mockMvc.perform(get("/api/orders/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }
}
