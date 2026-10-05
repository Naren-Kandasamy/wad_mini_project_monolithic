package com.example.shoppingcart.order.service;

import com.example.shoppingcart.cart.api.CartItemSnapshot;
import com.example.shoppingcart.order.api.CreateOrderCommand;
import com.example.shoppingcart.order.api.OrderApi;
import com.example.shoppingcart.order.api.OrderSnapshot;
import com.example.shoppingcart.order.dto.OrderResponse;
import com.example.shoppingcart.order.model.OrderDocument;
import com.example.shoppingcart.order.model.OrderItem;
import com.example.shoppingcart.order.repository.OrderRepository;
import com.example.shoppingcart.shared.error.OrderNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class OrderServiceImpl implements OrderService, OrderApi {

    private final OrderRepository orderRepository;

    public OrderServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public List<OrderResponse> getUserOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(OrderResponse::fromDocument)
            .toList();
    }

    @Override
    public OrderResponse getOrderById(String userId, String orderId) {
        OrderDocument doc = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderResponse.fromDocument(doc);
    }

    @Override
    public OrderResponse getAdminOrderById(String orderId) {
        OrderDocument doc = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderResponse.fromDocument(doc);
    }

    // --- OrderApi implementation ---

    @Override
    public OrderSnapshot createOrder(CreateOrderCommand command) {
        List<OrderItem> items = command.items().stream()
            .map(item -> new OrderItem(
                item.productId(),
                item.productName(),
                item.unitPrice(),
                item.quantity()
            ))
            .toList();

        Instant now = Instant.now();
        OrderDocument doc = new OrderDocument(
            null,
            command.userId(),
            command.idempotencyKey(),
            items,
            command.totalAmount(),
            "CONFIRMED",
            now,
            now
        );
        OrderDocument saved = orderRepository.save(doc);
        return toSnapshot(saved);
    }

    @Override
    public Optional<OrderSnapshot> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey) {
        return orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
            .map(this::toSnapshot);
    }

    @Override
    public Optional<OrderSnapshot> findByIdAndUserId(String orderId, String userId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
            .map(this::toSnapshot);
    }

    @Override
    public Optional<OrderSnapshot> findByIdForAdmin(String orderId) {
        return orderRepository.findById(orderId)
            .map(this::toSnapshot);
    }

    private OrderSnapshot toSnapshot(OrderDocument doc) {
        List<CartItemSnapshot> itemSnapshots = doc.getItems() != null
            ? doc.getItems().stream()
                .map(item -> new CartItemSnapshot(
                    item.getProductId(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getQuantity()
                ))
                .toList()
            : List.of();

        return new OrderSnapshot(
            doc.getId(),
            doc.getUserId(),
            doc.getIdempotencyKey(),
            itemSnapshots,
            doc.getTotalAmount(),
            doc.getStatus(),
            doc.getCreatedAt()
        );
    }
}
