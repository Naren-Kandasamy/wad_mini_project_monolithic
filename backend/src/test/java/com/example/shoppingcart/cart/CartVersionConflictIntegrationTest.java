package com.example.shoppingcart.cart;

import com.example.shoppingcart.cart.api.CartApi;
import com.example.shoppingcart.cart.api.CheckoutCart;
import com.example.shoppingcart.cart.dto.AddItemRequest;
import com.example.shoppingcart.cart.dto.UpdateQuantityRequest;
import com.example.shoppingcart.cart.model.CartDocument;
import com.example.shoppingcart.cart.repository.CartRepository;
import com.example.shoppingcart.cart.service.CartService;
import com.example.shoppingcart.checkout.service.CheckoutService;
import com.example.shoppingcart.order.repository.OrderRepository;
import com.example.shoppingcart.product.dto.CreateProductRequest;
import com.example.shoppingcart.product.dto.ProductResponse;
import com.example.shoppingcart.product.service.ProductService;
import com.example.shoppingcart.shared.error.CartVersionConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
class CartVersionConflictIntegrationTest {

    @Autowired
    private CartService cartService;

    @MockitoSpyBean
    private CartApi cartApi;

    @Autowired
    private ProductService productService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CheckoutService checkoutService;

    private String testProductId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();

        ProductResponse prod = productService.createProduct(new CreateProductRequest(
            "Wireless Mouse",
            "Ergonomic Optical Mouse",
            new BigDecimal("49.99"),
            "MS-OPT-" + UUID.randomUUID().toString().substring(0, 8),
            true
        ));
        testProductId = prod.id();
    }

    @Test
    @DisplayName("Optimistic Versioning: Concurrent cart mutation increments version, causing stale finalizeCheckout to abort")
    void finalizeCheckout_withStaleVersion_throwsCartVersionConflictException() {
        String userId = "user-version-test";

        // Step 1: User adds item (version becomes 1)
        cartService.addItem(userId, new AddItemRequest(testProductId, 1));
        CheckoutCart checkoutCart = cartApi.loadForCheckout(userId);
        long observedVersion = checkoutCart.version();

        // Step 2: Concurrent action mutates cart before checkout completes (version becomes 2)
        cartService.updateItemQuantity(userId, testProductId, new UpdateQuantityRequest(3));

        // Step 3: Attempting to finalize checkout with the stale version (version 1) must abort with 409
        assertThrows(CartVersionConflictException.class, () ->
            cartApi.finalizeCheckout(userId, observedVersion)
        );

        // Verify the cart was NOT cleared; the updated quantity (3) is safely preserved
        Optional<CartDocument> cart = cartRepository.findByUserId(userId);
        assertTrue(cart.isPresent());
        assertEquals(1, cart.get().getItems().size());
        assertEquals(3, cart.get().getItems().get(0).getQuantity(), "Newer cart state must be preserved without data loss");
    }

    @Test
    @DisplayName("Transaction Rollback on Version Conflict: Order is NOT committed if cart finalization conflicts")
    void checkoutTransaction_rollsBackOrder_onVersionConflict() {
        String userId = "user-trans-conflict";
        String idempotencyKey = "key-conflict-01";

        cartService.addItem(userId, new AddItemRequest(testProductId, 1));

        // Simulate a concurrent modification occurring just as checkout finalization is invoked
        doAnswer(invocation -> {
            cartRepository.findByUserId(userId).ifPresent(c -> {
                c.setVersion(c.getVersion() + 1);
                cartRepository.save(c);
            });
            return invocation.callRealMethod();
        }).when(cartApi).finalizeCheckout(eq(userId), anyLong());

        // Checkout attempt should fail on finalizeCheckout due to version mismatch
        assertThrows(CartVersionConflictException.class, () ->
            checkoutService.processCheckout(userId, idempotencyKey)
        );

        // Verify MongoDB transaction rollback: no order should exist in order repository!
        assertEquals(0, orderRepository.count(), "Order write must be rolled back on cart version conflict");
    }
}
