package com.example.shoppingcart.product.dto;

import com.example.shoppingcart.product.model.ProductDocument;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
    String id,
    String name,
    String description,
    BigDecimal price,
    String sku,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static ProductResponse fromDocument(ProductDocument doc) {
        return new ProductResponse(
            doc.getId(),
            doc.getName(),
            doc.getDescription(),
            doc.getPrice(),
            doc.getSku(),
            doc.isActive(),
            doc.getCreatedAt(),
            doc.getUpdatedAt()
        );
    }
}
