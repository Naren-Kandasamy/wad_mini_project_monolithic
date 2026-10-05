package com.example.shoppingcart.checkout.controller;

import com.example.shoppingcart.checkout.dto.CheckoutResponse;
import com.example.shoppingcart.checkout.service.CheckoutService;
import com.example.shoppingcart.shared.security.SecurityContextPrincipalResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final SecurityContextPrincipalResolver principalResolver;

    public CheckoutController(CheckoutService checkoutService, SecurityContextPrincipalResolver principalResolver) {
        this.checkoutService = checkoutService;
        this.principalResolver = principalResolver;
    }

    @PostMapping
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String userId = principalResolver.getCurrentUserId();
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
            ? idempotencyKey
            : UUID.randomUUID().toString();

        CheckoutResponse response = checkoutService.processCheckout(userId, effectiveKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
