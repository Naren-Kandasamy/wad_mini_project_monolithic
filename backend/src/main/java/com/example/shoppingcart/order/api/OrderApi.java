package com.example.shoppingcart.order.api;

import java.util.Optional;

public interface OrderApi {
    OrderSnapshot createOrder(CreateOrderCommand command);
    Optional<OrderSnapshot> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
    Optional<OrderSnapshot> findByIdAndUserId(String orderId, String userId);
    Optional<OrderSnapshot> findByIdForAdmin(String orderId);
}
