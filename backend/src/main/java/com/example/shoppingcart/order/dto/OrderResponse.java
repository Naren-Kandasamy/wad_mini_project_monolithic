package com.example.shoppingcart.order.dto;

import com.example.shoppingcart.order.api.OrderSnapshot;
import com.example.shoppingcart.order.model.OrderDocument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String id,
    String userId,
    String idempotencyKey,
    List<OrderItemResponse> items,
    BigDecimal totalAmount,
    String status,
    Instant createdAt
) {
    public static OrderResponse fromDocument(OrderDocument doc) {
        List<OrderItemResponse> itemResponses = doc.getItems() != null
            ? doc.getItems().stream().map(OrderItemResponse::fromModel).toList()
            : List.of();

        return new OrderResponse(
            doc.getId(),
            doc.getUserId(),
            doc.getIdempotencyKey(),
            itemResponses,
            doc.getTotalAmount(),
            doc.getStatus(),
            doc.getCreatedAt()
        );
    }

    public static OrderResponse fromSnapshot(OrderSnapshot snapshot) {
        List<OrderItemResponse> itemResponses = snapshot.items() != null
            ? snapshot.items().stream().map(OrderItemResponse::fromSnapshot).toList()
            : List.of();

        return new OrderResponse(
            snapshot.id(),
            snapshot.userId(),
            snapshot.idempotencyKey(),
            itemResponses,
            snapshot.totalAmount(),
            snapshot.status(),
            snapshot.createdAt()
        );
    }
}
