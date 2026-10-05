package com.example.shoppingcart.cart.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "carts")
public class CartDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private long version;
    private List<CartItem> items = new ArrayList<>();
    private Instant updatedAt;

    public CartDocument() {
    }

    public CartDocument(String id, String userId, long version, List<CartItem> items, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.version = version;
        this.items = items != null ? items : new ArrayList<>();
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public BigDecimal calculateSubtotal() {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
            .map(CartItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
