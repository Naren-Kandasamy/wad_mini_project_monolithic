package com.example.shoppingcart.cart.dto;

import com.example.shoppingcart.cart.model.CartDocument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
    String id,
    String userId,
    long version,
    List<CartItemResponse> items,
    BigDecimal subtotal,
    Instant updatedAt
) {
    public static CartResponse fromDocument(CartDocument doc) {
        List<CartItemResponse> itemResponses = doc.getItems() != null
            ? doc.getItems().stream().map(CartItemResponse::fromModel).toList()
            : List.of();

        return new CartResponse(
            doc.getId(),
            doc.getUserId(),
            doc.getVersion(),
            itemResponses,
            doc.calculateSubtotal(),
            doc.getUpdatedAt()
        );
    }
}
