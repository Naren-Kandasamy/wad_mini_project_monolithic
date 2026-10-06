package com.example.shoppingcart.product.service;

import com.example.shoppingcart.product.api.ProductSnapshot;
import com.example.shoppingcart.product.model.ProductDocument;
import com.example.shoppingcart.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceUnitTest {

    private ProductRepository productRepository;
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productService = new ProductServiceImpl(productRepository);
    }

    @Test
    @DisplayName("getAvailableProduct resolves by fallback ID prod-fallback-3 to SKU-MON-001")
    void shouldResolveFallbackIdToSku() {
        ProductDocument doc = new ProductDocument(
                "real-mongo-id-123",
                "27-inch Monitor",
                "QHD IPS 144 Hz gaming monitor",
                new BigDecimal("299.99"),
                "SKU-MON-001",
                true,
                Instant.now(),
                Instant.now()
        );

        when(productRepository.findById("prod-fallback-3")).thenReturn(Optional.empty());
        when(productRepository.findBySku("SKU-MON-001")).thenReturn(Optional.of(doc));

        Optional<ProductSnapshot> snapshot = productService.getAvailableProduct("prod-fallback-3");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().id()).isEqualTo("real-mongo-id-123");
        assertThat(snapshot.get().sku()).isEqualTo("SKU-MON-001");
        assertThat(snapshot.get().price()).isEqualByComparingTo(new BigDecimal("299.99"));
    }

    @Test
    @DisplayName("getAvailableProduct resolves by direct SKU when ID not found")
    void shouldResolveDirectSku() {
        ProductDocument doc = new ProductDocument(
                "real-mongo-id-456",
                "Wireless Mouse",
                "Ergonomic wireless optical mouse",
                new BigDecimal("34.99"),
                "SKU-MS-001",
                true,
                Instant.now(),
                Instant.now()
        );

        when(productRepository.findById("SKU-MS-001")).thenReturn(Optional.empty());
        when(productRepository.findBySku("SKU-MS-001")).thenReturn(Optional.of(doc));

        Optional<ProductSnapshot> snapshot = productService.getAvailableProduct("SKU-MS-001");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().id()).isEqualTo("real-mongo-id-456");
        assertThat(snapshot.get().name()).isEqualTo("Wireless Mouse");
    }
}
