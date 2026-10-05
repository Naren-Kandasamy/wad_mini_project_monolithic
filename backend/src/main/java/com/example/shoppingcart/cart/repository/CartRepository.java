package com.example.shoppingcart.cart.repository;

import com.example.shoppingcart.cart.model.CartDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends MongoRepository<CartDocument, String> {
    Optional<CartDocument> findByUserId(String userId);
}
