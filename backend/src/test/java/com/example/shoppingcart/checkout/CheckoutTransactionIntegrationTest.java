package com.example.shoppingcart.checkout;

import com.example.shoppingcart.cart.dto.AddItemRequest;
import com.example.shoppingcart.cart.model.CartDocument;
import com.example.shoppingcart.cart.repository.CartRepository;
import com.example.shoppingcart.cart.service.CartService;
import com.example.shoppingcart.checkout.dto.CheckoutResponse;
import com.example.shoppingcart.checkout.service.CheckoutService;
import com.example.shoppingcart.order.model.OrderDocument;
import com.example.shoppingcart.order.repository.OrderRepository;
import com.example.shoppingcart.product.dto.CreateProductRequest;
import com.example.shoppingcart.product.dto.ProductResponse;
import com.example.shoppingcart.product.repository.ProductRepository;
import com.example.shoppingcart.product.service.ProductService;
import com.example.shoppingcart.shared.error.CartEmptyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutTransactionIntegrationTest {

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    private String testProductId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();

        ProductResponse prod = productService.createProduct(new CreateProductRequest(
            "Mechanical Keyboard",
            "RGB Mechanical Gaming Keyboard",
            new BigDecimal("99.99"),
            "KB-RGB-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            true
        ));
        testProductId = prod.id();
    }

    @Test
    @DisplayName("Transactional Checkout: Persists Order with CONFIRMED and empties Cart atomically")
    void checkout_successfulTransaction_createsOrderAndClearsCart() {
        String userId = "user-trans-1";
        String idempotencyKey = "key-success-01";

        // Add item to cart
        cartService.addItem(userId, new AddItemRequest(testProductId, 2));

        // Execute checkout transaction
        CheckoutResponse response = checkoutService.processCheckout(userId, idempotencyKey);

        assertNotNull(response);
        assertNotNull(response.orderId());
        assertEquals("CONFIRMED", response.status());
        assertEquals(new BigDecimal("199.98"), response.totalAmount());
        assertEquals(1, response.items().size());
        assertEquals(testProductId, response.items().get(0).productId());
        assertEquals(2, response.items().get(0).quantity());

        // Verify Cart is cleared in MongoDB
        Optional<CartDocument> updatedCart = cartRepository.findByUserId(userId);
        assertTrue(updatedCart.isPresent());
        assertTrue(updatedCart.get().getItems().isEmpty(), "Cart items must be cleared after checkout");

        // Verify Order is persisted in MongoDB
        Optional<OrderDocument> persistedOrder = orderRepository.findById(response.orderId());
        assertTrue(persistedOrder.isPresent());
        assertEquals("CONFIRMED", persistedOrder.get().getStatus());
        assertEquals(idempotencyKey, persistedOrder.get().getIdempotencyKey());
    }

    @Test
    @DisplayName("Transactional Rollback: When cart is empty, transaction aborts without creating order")
    void checkout_emptyCart_rollsBackWithoutCreatingOrder() {
        String userId = "user-trans-empty";
        String idempotencyKey = "key-empty-01";

        assertThrows(CartEmptyException.class, () ->
            checkoutService.processCheckout(userId, idempotencyKey)
        );

        long orderCount = orderRepository.count();
        assertEquals(0, orderCount, "No order should be created when cart is empty");
    }

    @Test
    @DisplayName("Idempotency Replay: Duplicate checkout request returns exact same order without duplicate write")
    void checkout_idempotencyReplay_returnsExistingOrder() {
        String userId = "user-trans-idem";
        String idempotencyKey = "key-idem-unique";

        cartService.addItem(userId, new AddItemRequest(testProductId, 1));

        // First checkout
        CheckoutResponse firstResponse = checkoutService.processCheckout(userId, idempotencyKey);
        assertNotNull(firstResponse);

        // Replay with identical idempotency key
        CheckoutResponse secondResponse = checkoutService.processCheckout(userId, idempotencyKey);

        assertEquals(firstResponse.orderId(), secondResponse.orderId());
        assertEquals(firstResponse.totalAmount(), secondResponse.totalAmount());
        assertEquals(firstResponse.createdAt().toEpochMilli(), secondResponse.createdAt().toEpochMilli());

        // Verify only 1 order exists in database
        assertEquals(1, orderRepository.count());
    }
}
