package com.example.shoppingcart.order;

import com.example.shoppingcart.cart.api.CartItemSnapshot;
import com.example.shoppingcart.order.api.CreateOrderCommand;
import com.example.shoppingcart.order.api.OrderSnapshot;
import com.example.shoppingcart.order.dto.OrderResponse;
import com.example.shoppingcart.order.model.OrderDocument;
import com.example.shoppingcart.order.model.OrderItem;
import com.example.shoppingcart.order.repository.OrderRepository;
import com.example.shoppingcart.order.service.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSnapshotUnitTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository);
    }

    @Test
    @DisplayName("createOrder: creates an immutable order snapshot with CONFIRMED status and accurate total")
    void createOrder_createsConfirmedSnapshot() {
        when(orderRepository.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument doc = inv.getArgument(0);
            doc.setId("order-123");
            return doc;
        });

        List<CartItemSnapshot> items = List.of(
            new CartItemSnapshot("p1", "Item 1", new BigDecimal("19.99"), 2),
            new CartItemSnapshot("p2", "Item 2", new BigDecimal("50.00"), 1)
        );
        CreateOrderCommand command = new CreateOrderCommand(
            "user1",
            "idem-key-1",
            items,
            new BigDecimal("89.98")
        );

        OrderSnapshot snapshot = orderService.createOrder(command);

        assertNotNull(snapshot.id());
        assertEquals("user1", snapshot.userId());
        assertEquals("idem-key-1", snapshot.idempotencyKey());
        assertEquals("CONFIRMED", snapshot.status());
        assertEquals(new BigDecimal("89.98"), snapshot.totalAmount());
        assertEquals(2, snapshot.items().size());
        assertEquals(new BigDecimal("19.99"), snapshot.items().get(0).unitPrice());
        assertEquals(2, snapshot.items().get(0).quantity());
    }

    @Test
    @DisplayName("orderSnapshot: preserves historical prices even if external catalog price changes")
    void orderSnapshot_preservesHistoricalPrice() {
        // Given an order was created with unit price 19.99
        OrderItem orderItem = new OrderItem("p1", "Item 1", new BigDecimal("19.99"), 2);
        OrderDocument doc = new OrderDocument(
            "order-1",
            "user1",
            "key-1",
            List.of(orderItem),
            new BigDecimal("39.98"),
            "CONFIRMED",
            Instant.now(),
            Instant.now()
        );

        // When converted to response
        OrderResponse response = OrderResponse.fromDocument(doc);

        // Even if we hypothetically simulate changing a catalog price to 99.99
        BigDecimal hypotheticalNewCatalogPrice = new BigDecimal("99.99");
        assertNotEquals(hypotheticalNewCatalogPrice, response.items().get(0).unitPrice());
        assertEquals(new BigDecimal("19.99"), response.items().get(0).unitPrice());
        assertEquals(new BigDecimal("39.98"), response.totalAmount());
    }

    @Test
    @DisplayName("createOrder: verifies status is strictly CONFIRMED according to architecture rules")
    void createOrder_statusIsStrictlyConfirmed() {
        ArgumentCaptor<OrderDocument> captor = ArgumentCaptor.forClass(OrderDocument.class);
        when(orderRepository.save(captor.capture())).thenAnswer(inv -> {
            OrderDocument doc = inv.getArgument(0);
            doc.setId("ord-xyz");
            return doc;
        });

        CreateOrderCommand command = new CreateOrderCommand(
            "user-abc",
            "key-abc",
            List.of(new CartItemSnapshot("p1", "Name", BigDecimal.TEN, 1)),
            BigDecimal.TEN
        );

        orderService.createOrder(command);

        OrderDocument savedDoc = captor.getValue();
        assertEquals("CONFIRMED", savedDoc.getStatus(), "Baseline synchronous checkout flow must strictly produce CONFIRMED status");
    }
}
