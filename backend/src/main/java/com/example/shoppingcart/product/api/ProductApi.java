package com.example.shoppingcart.product.api;

import java.util.Optional;

public interface ProductApi {
    Optional<ProductSnapshot> getAvailableProduct(String productId);
}
