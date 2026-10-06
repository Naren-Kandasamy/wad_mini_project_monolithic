package com.example.shoppingcart.product.repository;

import com.example.shoppingcart.product.model.ProductDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<ProductDocument, String> {
    Optional<ProductDocument> findBySku(String sku);
    Optional<ProductDocument> findByName(String name);
    List<ProductDocument> findByActiveTrue();
}
