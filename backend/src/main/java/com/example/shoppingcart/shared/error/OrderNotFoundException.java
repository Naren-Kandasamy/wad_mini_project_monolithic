package com.example.shoppingcart.shared.error;

public class OrderNotFoundException extends ResourceNotFoundException {
    public OrderNotFoundException(String orderId) {
        super("Order not found with id: " + orderId);
    }
}
