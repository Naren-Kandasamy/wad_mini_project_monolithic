package com.example.shoppingcart.product;

import com.example.shoppingcart.product.api.ProductSnapshot;
import com.example.shoppingcart.product.dto.CreateProductRequest;
import com.example.shoppingcart.product.dto.ProductResponse;
import com.example.shoppingcart.product.dto.UpdateProductRequest;
import com.example.shoppingcart.product.model.ProductDocument;
import com.example.shoppingcart.product.repository.ProductRepository;
import com.example.shoppingcart.product.service.ProductServiceImpl;
import com.example.shoppingcart.shared.error.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductValidationUnitTest {

    @Mock
    private ProductRepository productRepository;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository);
    }

    @Test
    @DisplayName("getAvailableProduct: returns product only when active is true")
    void getAvailableProduct_returnsOnlyActive() {
        ProductDocument activeDoc = new ProductDocument("p1", "Active Item", "Desc", BigDecimal.TEN, "SKU-ACT", true, Instant.now(), Instant.now());
        ProductDocument inactiveDoc = new ProductDocument("p2", "Inactive Item", "Desc", BigDecimal.ONE, "SKU-INACT", false, Instant.now(), Instant.now());

        when(productRepository.findById("p1")).thenReturn(Optional.of(activeDoc));
        when(productRepository.findById("p2")).thenReturn(Optional.of(inactiveDoc));

        Optional<ProductSnapshot> activeResult = productService.getAvailableProduct("p1");
        assertTrue(activeResult.isPresent());
        assertEquals("p1", activeResult.get().id());
        assertTrue(activeResult.get().active());

        Optional<ProductSnapshot> inactiveResult = productService.getAvailableProduct("p2");
        assertTrue(inactiveResult.isEmpty(), "Inactive products should not be available for cart/checkout");
    }

    @Test
    @DisplayName("createProduct: persists product and returns proper response")
    void createProduct_persistsAndReturnsResponse() {
        when(productRepository.save(any(ProductDocument.class))).thenAnswer(inv -> {
            ProductDocument doc = inv.getArgument(0);
            doc.setId("gen-id-1");
            return doc;
        });

        CreateProductRequest request = new CreateProductRequest("Headphones", "Noise cancelling", new BigDecimal("199.99"), "SKU-HP-1", true);
        ProductResponse response = productService.createProduct(request);

        assertNotNull(response.id());
        assertEquals("Headphones", response.name());
        assertEquals(new BigDecimal("199.99"), response.price());
        assertEquals("SKU-HP-1", response.sku());
        assertTrue(response.active());
    }

    @Test
    @DisplayName("deleteProduct: performs soft delete by marking active as false")
    void deleteProduct_performsSoftDelete() {
        ProductDocument doc = new ProductDocument("p1", "Item", "Desc", BigDecimal.TEN, "SKU-1", true, Instant.now(), Instant.now());
        when(productRepository.findById("p1")).thenReturn(Optional.of(doc));

        productService.deleteProduct("p1");

        assertFalse(doc.isActive());
        verify(productRepository).save(doc);
    }

    @Test
    @DisplayName("getProductById: throws ProductNotFoundException when missing")
    void getProductById_throwsWhenNotFound() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.getProductById("missing"));
    }
}
