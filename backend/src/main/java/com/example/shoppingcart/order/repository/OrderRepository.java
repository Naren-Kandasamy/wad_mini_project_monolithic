package com.example.shoppingcart.order.repository;

import com.example.shoppingcart.order.model.OrderDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends MongoRepository<OrderDocument, String> {
    Optional<OrderDocument> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
    Optional<OrderDocument> findByIdAndUserId(String id, String userId);
    List<OrderDocument> findByUserIdOrderByCreatedAtDesc(String userId);
}
