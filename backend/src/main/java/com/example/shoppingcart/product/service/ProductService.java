package com.example.shoppingcart.product.service;

import com.example.shoppingcart.product.api.ProductApi;
import com.example.shoppingcart.product.dto.CreateProductRequest;
import com.example.shoppingcart.product.dto.ProductResponse;
import com.example.shoppingcart.product.dto.UpdateProductRequest;

import java.util.List;

public interface ProductService extends ProductApi {
    List<ProductResponse> listActiveProducts();
    ProductResponse getProductById(String id);
    ProductResponse createProduct(CreateProductRequest request);
    ProductResponse updateProduct(String id, UpdateProductRequest request);
    void deleteProduct(String id);
}
