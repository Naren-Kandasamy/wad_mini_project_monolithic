package com.example.shoppingcart.order.dto;

import com.example.shoppingcart.cart.api.CartItemSnapshot;
import com.example.shoppingcart.order.model.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
    String productId,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal lineTotal
) {
    public static OrderItemResponse fromModel(OrderItem item) {
        return new OrderItemResponse(
            item.getProductId(),
            item.getProductName(),
            item.getUnitPrice(),
            item.getQuantity(),
            item.getLineTotal()
        );
    }

    public static OrderItemResponse fromSnapshot(CartItemSnapshot snapshot) {
        BigDecimal lineTotal = snapshot.unitPrice() != null
            ? snapshot.unitPrice().multiply(BigDecimal.valueOf(snapshot.quantity()))
            : BigDecimal.ZERO;
        return new OrderItemResponse(
            snapshot.productId(),
            snapshot.productName(),
            snapshot.unitPrice(),
            snapshot.quantity(),
            lineTotal
        );
    }
}
