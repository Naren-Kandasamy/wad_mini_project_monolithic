package com.example.shoppingcart.cart.dto;

import com.example.shoppingcart.cart.model.CartItem;

import java.math.BigDecimal;

public record CartItemResponse(
    String productId,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal lineTotal
) {
    public static CartItemResponse fromModel(CartItem item) {
        return new CartItemResponse(
            item.getProductId(),
            item.getProductName(),
            item.getUnitPrice(),
            item.getQuantity(),
            item.getLineTotal()
        );
    }
}
