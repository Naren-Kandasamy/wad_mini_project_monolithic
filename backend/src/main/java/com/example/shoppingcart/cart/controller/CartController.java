package com.example.shoppingcart.cart.controller;

import com.example.shoppingcart.cart.dto.AddItemRequest;
import com.example.shoppingcart.cart.dto.CartResponse;
import com.example.shoppingcart.cart.dto.UpdateQuantityRequest;
import com.example.shoppingcart.cart.service.CartService;
import com.example.shoppingcart.shared.security.SecurityContextPrincipalResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carts/me")
public class CartController {

    private final CartService cartService;
    private final SecurityContextPrincipalResolver principalResolver;

    public CartController(CartService cartService, SecurityContextPrincipalResolver principalResolver) {
        this.cartService = cartService;
        this.principalResolver = principalResolver;
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart() {
        String userId = principalResolver.getCurrentUserId();
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddItemRequest request) {
        String userId = principalResolver.getCurrentUserId();
        return ResponseEntity.ok(cartService.addItem(userId, request));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable String productId,
            @Valid @RequestBody UpdateQuantityRequest request) {
        String userId = principalResolver.getCurrentUserId();
        return ResponseEntity.ok(cartService.updateItemQuantity(userId, productId, request));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable String productId) {
        String userId = principalResolver.getCurrentUserId();
        return ResponseEntity.ok(cartService.removeItem(userId, productId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        String userId = principalResolver.getCurrentUserId();
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
