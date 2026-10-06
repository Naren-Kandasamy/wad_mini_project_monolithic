package com.example.shoppingcart.product;

import com.example.shoppingcart.product.dto.CreateProductRequest;
import com.example.shoppingcart.product.model.ProductDocument;
import com.example.shoppingcart.product.repository.ProductRepository;
import com.example.shoppingcart.product.seeder.ProductDataSeeder;
import com.example.shoppingcart.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductDataSeederTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductDataSeeder seeder;

    @Test
    @DisplayName("Seeds all products when repository is empty")
    void seedsAllProductsWhenEmpty() {
        when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());

        seeder.run();

        verify(productService, times(7)).createProduct(any(CreateProductRequest.class));
    }

    @Test
    @DisplayName("Skips seeding products when SKUs already exist (idempotent)")
    void skipsSeedingWhenAlreadyPresent() {
        java.time.Instant now = java.time.Instant.now();
        ProductDocument doc = new ProductDocument("id-1", "Existing", "Desc", new BigDecimal("10.00"), "SKU-KB-001", true, now, now);
        when(productRepository.findBySku(anyString())).thenReturn(Optional.of(doc));
        when(productRepository.findBySku("SKU-MS-001")).thenReturn(Optional.empty());

        seeder.run();

        verify(productService, times(1)).createProduct(any(CreateProductRequest.class));
    }
}
