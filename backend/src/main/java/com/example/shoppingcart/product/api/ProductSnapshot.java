package com.example.shoppingcart.product.api;

import java.math.BigDecimal;

public record ProductSnapshot(
    String id,
    String name,
    String description,
    BigDecimal price,
    String sku,
    boolean active
) {}
