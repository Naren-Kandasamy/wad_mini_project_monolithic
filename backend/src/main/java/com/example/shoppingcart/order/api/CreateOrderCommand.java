package com.example.shoppingcart.order.api;

import com.example.shoppingcart.cart.api.CartItemSnapshot;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderCommand(
    String userId,
    String idempotencyKey,
    List<CartItemSnapshot> items,
    BigDecimal totalAmount
) {}
