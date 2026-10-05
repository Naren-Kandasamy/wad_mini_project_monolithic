package com.example.shoppingcart.order.api;

import com.example.shoppingcart.cart.api.CartItemSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderSnapshot(
    String id,
    String userId,
    String idempotencyKey,
    List<CartItemSnapshot> items,
    BigDecimal totalAmount,
    String status,
    Instant createdAt
) {}
