package com.example.shoppingcart.cart.api;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutCart(
    String userId,
    long version,
    List<CartItemSnapshot> items,
    BigDecimal subtotal
) {
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}
