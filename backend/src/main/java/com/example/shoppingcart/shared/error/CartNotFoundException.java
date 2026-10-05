package com.example.shoppingcart.shared.error;

public class CartNotFoundException extends ResourceNotFoundException {
    public CartNotFoundException(String userId) {
        super("Cart not found for user: " + userId);
    }
}
