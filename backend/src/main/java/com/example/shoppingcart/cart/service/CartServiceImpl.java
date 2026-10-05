package com.example.shoppingcart.cart.service;

import com.example.shoppingcart.cart.api.CartApi;
import com.example.shoppingcart.cart.api.CartItemSnapshot;
import com.example.shoppingcart.cart.api.CheckoutCart;
import com.example.shoppingcart.cart.dto.AddItemRequest;
import com.example.shoppingcart.cart.dto.CartResponse;
import com.example.shoppingcart.cart.dto.UpdateQuantityRequest;
import com.example.shoppingcart.cart.model.CartDocument;
import com.example.shoppingcart.cart.model.CartItem;
import com.example.shoppingcart.cart.repository.CartRepository;
import com.example.shoppingcart.product.api.ProductApi;
import com.example.shoppingcart.product.api.ProductSnapshot;
import com.example.shoppingcart.shared.error.CartEmptyException;
import com.example.shoppingcart.shared.error.CartNotFoundException;
import com.example.shoppingcart.shared.error.CartVersionConflictException;
import com.example.shoppingcart.shared.error.ProductNotFoundException;
import com.example.shoppingcart.shared.error.ResourceNotFoundException;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartServiceImpl implements CartService, CartApi {

    private final CartRepository cartRepository;
    private final ProductApi productApi;
    private final MongoTemplate mongoTemplate;

    public CartServiceImpl(CartRepository cartRepository, ProductApi productApi, MongoTemplate mongoTemplate) {
        this.cartRepository = cartRepository;
        this.productApi = productApi;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public CartResponse getCart(String userId) {
        CartDocument cart = cartRepository.findByUserId(userId)
            .orElseGet(() -> createEmptyCart(userId));
        return CartResponse.fromDocument(cart);
    }

    @Override
    public CartResponse addItem(String userId, AddItemRequest request) {
        ProductSnapshot product = productApi.getAvailableProduct(request.productId())
            .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        CartDocument cart = cartRepository.findByUserId(userId)
            .orElseGet(() -> new CartDocument(null, userId, 0L, new ArrayList<>(), Instant.now()));

        List<CartItem> items = cart.getItems();
        Optional<CartItem> existingItem = items.stream()
            .filter(item -> item.getProductId().equals(product.id()))
            .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.quantity());
            item.setUnitPrice(product.price()); // refresh with current active catalog price
        } else {
            items.add(new CartItem(product.id(), product.name(), product.price(), request.quantity()));
        }

        cart.setVersion(cart.getVersion() + 1);
        cart.setUpdatedAt(Instant.now());
        CartDocument saved = cartRepository.save(cart);
        return CartResponse.fromDocument(saved);
    }

    @Override
    public CartResponse updateItemQuantity(String userId, String productId, UpdateQuantityRequest request) {
        CartDocument cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new CartNotFoundException(userId));

        List<CartItem> items = cart.getItems();
        CartItem targetItem = items.stream()
            .filter(item -> item.getProductId().equals(productId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Item with product ID " + productId + " not found in cart"));

        if (request.quantity() <= 0) {
            items.remove(targetItem);
        } else {
            targetItem.setQuantity(request.quantity());
        }

        cart.setVersion(cart.getVersion() + 1);
        cart.setUpdatedAt(Instant.now());
        CartDocument saved = cartRepository.save(cart);
        return CartResponse.fromDocument(saved);
    }

    @Override
    public CartResponse removeItem(String userId, String productId) {
        CartDocument cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new CartNotFoundException(userId));

        boolean removed = cart.getItems().removeIf(item -> item.getProductId().equals(productId));
        if (!removed) {
            throw new ResourceNotFoundException("Item with product ID " + productId + " not found in cart");
        }

        cart.setVersion(cart.getVersion() + 1);
        cart.setUpdatedAt(Instant.now());
        CartDocument saved = cartRepository.save(cart);
        return CartResponse.fromDocument(saved);
    }

    @Override
    public void clearCart(String userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cart.setVersion(cart.getVersion() + 1);
            cart.setUpdatedAt(Instant.now());
            cartRepository.save(cart);
        });
    }

    // --- CartApi implementation for Checkout coordination ---

    @Override
    public CheckoutCart loadForCheckout(String userId) {
        CartDocument cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new CartEmptyException("Cart is empty for user: " + userId));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new CartEmptyException("Cart is empty for user: " + userId);
        }

        List<CartItemSnapshot> snapshots = cart.getItems().stream()
            .map(item -> new CartItemSnapshot(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity()
            ))
            .toList();

        return new CheckoutCart(cart.getUserId(), cart.getVersion(), snapshots, cart.calculateSubtotal());
    }

    @Override
    public void finalizeCheckout(String userId, long expectedVersion) {
        Query query = Query.query(
            Criteria.where("userId").is(userId)
                .and("version").is(expectedVersion)
        );
        Update update = new Update()
            .set("items", List.of())
            .inc("version", 1)
            .set("updatedAt", Instant.now());

        UpdateResult result = mongoTemplate.updateFirst(query, update, CartDocument.class);
        if (result.getModifiedCount() == 0) {
            throw new CartVersionConflictException(
                "Cart version conflict for user: " + userId + ". Expected version: " + expectedVersion
            );
        }
    }

    private CartDocument createEmptyCart(String userId) {
        CartDocument cart = new CartDocument(null, userId, 0L, new ArrayList<>(), Instant.now());
        return cartRepository.save(cart);
    }
}
