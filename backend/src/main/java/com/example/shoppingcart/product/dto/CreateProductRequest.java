package com.example.shoppingcart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateProductRequest(
    @NotBlank(message = "Product name cannot be blank")
    String name,

    String description,

    @NotNull(message = "Product price is required")
    @DecimalMin(value = "0.01", message = "Product price must be greater than zero")
    BigDecimal price,

    @NotBlank(message = "Product SKU cannot be blank")
    String sku,

    Boolean active
) {}
