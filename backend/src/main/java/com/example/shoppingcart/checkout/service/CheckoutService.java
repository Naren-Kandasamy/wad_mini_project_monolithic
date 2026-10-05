package com.example.shoppingcart.checkout.service;

import com.example.shoppingcart.cart.api.CartApi;
import com.example.shoppingcart.cart.api.CheckoutCart;
import com.example.shoppingcart.checkout.dto.CheckoutResponse;
import com.example.shoppingcart.order.api.CreateOrderCommand;
import com.example.shoppingcart.order.api.OrderApi;
import com.example.shoppingcart.order.api.OrderSnapshot;
import com.example.shoppingcart.shared.error.CartEmptyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CartApi cartApi;
    private final OrderApi orderApi;

    public CheckoutService(CartApi cartApi, OrderApi orderApi) {
        this.cartApi = cartApi;
        this.orderApi = orderApi;
    }

    @Transactional
    public CheckoutResponse processCheckout(String userId, String idempotencyKey) {
        // 1. Check if order with (userId, idempotencyKey) already exists -> idempotency replay
        Optional<OrderSnapshot> existingOrder = orderApi.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingOrder.isPresent()) {
            log.info("Idempotent replay detected for user {} with key {}", userId, idempotencyKey);
            return CheckoutResponse.fromOrderSnapshot(existingOrder.get());
        }

        // 2. Load cart snapshot
        CheckoutCart cart = cartApi.loadForCheckout(userId);
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            throw new CartEmptyException("Cannot checkout with an empty cart for user: " + userId);
        }

        // 3. Total amount calculated strictly from server-owned cart snapshot items
        BigDecimal totalAmount = cart.subtotal();

        // 4. Create and persist order with status CONFIRMED
        CreateOrderCommand command = new CreateOrderCommand(
            userId,
            idempotencyKey,
            cart.items(),
            totalAmount
        );

        OrderSnapshot createdOrder;
        try {
            createdOrder = orderApi.createOrder(command);
        } catch (DuplicateKeyException e) {
            // Concurrent race condition: another thread committed the order with the same key
            log.warn("Concurrent duplicate key race detected for user {} with key {}. Re-fetching committed order.", userId, idempotencyKey);
            return orderApi.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(CheckoutResponse::fromOrderSnapshot)
                .orElseThrow(() -> e);
        }

        // 5. Finalize cart with optimistic concurrency check (throws CartVersionConflictException on mismatch)
        cartApi.finalizeCheckout(userId, cart.version());

        return CheckoutResponse.fromOrderSnapshot(createdOrder);
    }
}
