package com.example.shoppingcart.checkout.dto;

import com.example.shoppingcart.cart.api.CartItemSnapshot;
import com.example.shoppingcart.order.api.OrderSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CheckoutResponse(
    String orderId,
    String userId,
    String idempotencyKey,
    BigDecimal totalAmount,
    String status,
    Instant createdAt,
    List<CartItemSnapshot> items
) {
    public static CheckoutResponse fromOrderSnapshot(OrderSnapshot snapshot) {
        return new CheckoutResponse(
            snapshot.id(),
            snapshot.userId(),
            snapshot.idempotencyKey(),
            snapshot.totalAmount(),
            snapshot.status(),
            snapshot.createdAt(),
            snapshot.items()
        );
    }
}
