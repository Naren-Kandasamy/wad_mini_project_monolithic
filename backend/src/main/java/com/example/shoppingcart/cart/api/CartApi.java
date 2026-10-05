package com.example.shoppingcart.cart.api;

public interface CartApi {
    CheckoutCart loadForCheckout(String userId);
    void finalizeCheckout(String userId, long expectedVersion);
}
