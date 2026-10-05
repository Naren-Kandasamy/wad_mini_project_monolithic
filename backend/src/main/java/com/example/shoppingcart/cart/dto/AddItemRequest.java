package com.example.shoppingcart.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddItemRequest(
    @NotBlank(message = "Product ID is required")
    String productId,

    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity
) {}
