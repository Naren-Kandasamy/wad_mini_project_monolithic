package com.example.shoppingcart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record UpdateProductRequest(
    String name,
    String description,
    @DecimalMin(value = "0.01", message = "Product price must be greater than zero")
    BigDecimal price,
    Boolean active
) {}
