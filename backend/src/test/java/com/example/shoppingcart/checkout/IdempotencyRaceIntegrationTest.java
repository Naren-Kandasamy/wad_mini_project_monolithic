package com.example.shoppingcart.checkout;

import com.example.shoppingcart.cart.dto.AddItemRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyRaceIntegrationTest {

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
            "USB-C Hub",
            "Multiport Adapter 7-in-1",
            new BigDecimal("35.00"),
            "HUB-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            true
        ));
        testProductId = prod.id();
    }

    @Test
    @DisplayName("Concurrent Idempotency Race: Concurrent requests with identical idempotency key produce exactly 1 order")
    void concurrentCheckout_withSameIdempotencyKey_createsExactlyOneOrder() throws Exception {
        String userId = "user-race-01";
        String idempotencyKey = "race-key-identical";

        cartService.addItem(userId, new AddItemRequest(testProductId, 1));

        int concurrency = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<CheckoutResponse> task = () -> {
            startLatch.await();
            return checkoutService.processCheckout(userId, idempotencyKey);
        };

        Future<CheckoutResponse> future1 = executor.submit(task);
        Future<CheckoutResponse> future2 = executor.submit(task);

        // Fire both threads simultaneously
        startLatch.countDown();

        CheckoutResponse resp1 = null;
        CheckoutResponse resp2 = null;
        Exception error1 = null;
        Exception error2 = null;

        try {
            resp1 = future1.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            error1 = e;
        }

        try {
            resp2 = future2.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            error2 = e;
        }

        executor.shutdown();

        // At least one must succeed; if one succeeded and the other encountered version conflict / duplicate key,
        // let's verify database consistency: exactly 1 order in database
        List<OrderDocument> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertEquals(1, orders.size(), "MongoDB compound index must ensure exactly 1 order exists for the user and key");

        if (resp1 != null && resp2 != null) {
            assertEquals(resp1.orderId(), resp2.orderId(), "Both responses must refer to the exact same order");
        }
    }
}
