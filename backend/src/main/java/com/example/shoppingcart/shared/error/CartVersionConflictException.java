package com.example.shoppingcart.shared.error;

public class CartVersionConflictException extends RuntimeException {
    public CartVersionConflictException(String message) {
        super(message);
    }
}
