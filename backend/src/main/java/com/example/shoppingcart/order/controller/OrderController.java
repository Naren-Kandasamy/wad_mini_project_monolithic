package com.example.shoppingcart.order.controller;

import com.example.shoppingcart.order.dto.OrderResponse;
import com.example.shoppingcart.order.service.OrderService;
import com.example.shoppingcart.shared.security.SecurityContextPrincipalResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final SecurityContextPrincipalResolver principalResolver;

    public OrderController(OrderService orderService, SecurityContextPrincipalResolver principalResolver) {
        this.orderService = orderService;
        this.principalResolver = principalResolver;
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getUserOrders() {
        String userId = principalResolver.getCurrentUserId();
        return ResponseEntity.ok(orderService.getUserOrders(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable String id) {
        String userId = principalResolver.getCurrentUserId();
        return ResponseEntity.ok(orderService.getOrderById(userId, id));
    }

    @GetMapping("/admin/{orderId}")
    public ResponseEntity<OrderResponse> getAdminOrderById(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getAdminOrderById(orderId));
    }
}
