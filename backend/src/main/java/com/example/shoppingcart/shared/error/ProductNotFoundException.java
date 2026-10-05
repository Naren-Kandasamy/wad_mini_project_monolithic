package com.example.shoppingcart.shared.error;

public class ProductNotFoundException extends ResourceNotFoundException {
    public ProductNotFoundException(String productId) {
        super("Product not found with id: " + productId);
    }
}
