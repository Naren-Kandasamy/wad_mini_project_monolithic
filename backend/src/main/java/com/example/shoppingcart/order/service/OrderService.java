package com.example.shoppingcart.order.service;

import com.example.shoppingcart.order.dto.OrderResponse;

import java.util.List;

public interface OrderService {
    List<OrderResponse> getUserOrders(String userId);
    OrderResponse getOrderById(String userId, String orderId);
    OrderResponse getAdminOrderById(String orderId);
}
