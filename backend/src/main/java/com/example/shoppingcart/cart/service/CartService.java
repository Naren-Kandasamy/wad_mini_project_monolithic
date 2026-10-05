package com.example.shoppingcart.cart.service;

import com.example.shoppingcart.cart.dto.AddItemRequest;
import com.example.shoppingcart.cart.dto.CartResponse;
import com.example.shoppingcart.cart.dto.UpdateQuantityRequest;

public interface CartService {
    CartResponse getCart(String userId);
    CartResponse addItem(String userId, AddItemRequest request);
    CartResponse updateItemQuantity(String userId, String productId, UpdateQuantityRequest request);
    CartResponse removeItem(String userId, String productId);
    void clearCart(String userId);
}
