package com.example.shoppingcart.cart.api;

import java.math.BigDecimal;

public record CartItemSnapshot(
    String productId,
    String productName,
    BigDecimal unitPrice,
    int quantity
) {}
