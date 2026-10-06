package com.example.shoppingcart.product.service;

import com.example.shoppingcart.product.api.ProductSnapshot;
import com.example.shoppingcart.product.dto.CreateProductRequest;
import com.example.shoppingcart.product.dto.ProductResponse;
import com.example.shoppingcart.product.dto.UpdateProductRequest;
import com.example.shoppingcart.product.model.ProductDocument;
import com.example.shoppingcart.product.repository.ProductRepository;
import com.example.shoppingcart.shared.error.ProductNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    private static final java.util.Map<String, String> FALLBACK_SKU_MAP = java.util.Map.of(
        "prod-fallback-1", "SKU-KB-001",
        "prod-fallback-2", "SKU-MS-001",
        "prod-fallback-3", "SKU-MON-001",
        "prod-fallback-4", "SKU-KB-002",
        "prod-fallback-5", "SKU-MON-002",
        "prod-fallback-6", "SKU-AUD-001",
        "prod-fallback-7", "SKU-PER-001"
    );

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Optional<ProductSnapshot> getAvailableProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return Optional.empty();
        }

        Optional<ProductDocument> docOpt = productRepository.findById(productId);

        if (docOpt.isEmpty()) {
            docOpt = productRepository.findBySku(productId);
        }

        if (docOpt.isEmpty() && FALLBACK_SKU_MAP.containsKey(productId)) {
            docOpt = productRepository.findBySku(FALLBACK_SKU_MAP.get(productId));
        }

        if (docOpt.isEmpty()) {
            docOpt = productRepository.findByName(productId);
        }

        return docOpt
            .filter(ProductDocument::isActive)
            .map(doc -> new ProductSnapshot(
                doc.getId(),
                doc.getName(),
                doc.getDescription(),
                doc.getPrice(),
                doc.getSku(),
                doc.isActive()
            ));
    }

    @Override
    public List<ProductResponse> listActiveProducts() {
        return productRepository.findByActiveTrue().stream()
            .map(ProductResponse::fromDocument)
            .toList();
    }

    @Override
    public ProductResponse getProductById(String id) {
        Optional<ProductDocument> docOpt = productRepository.findById(id);

        if (docOpt.isEmpty()) {
            docOpt = productRepository.findBySku(id);
        }

        if (docOpt.isEmpty() && FALLBACK_SKU_MAP.containsKey(id)) {
            docOpt = productRepository.findBySku(FALLBACK_SKU_MAP.get(id));
        }

        ProductDocument doc = docOpt.orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.fromDocument(doc);
    }

    @Override
    public ProductResponse createProduct(CreateProductRequest request) {
        Instant now = Instant.now();
        ProductDocument doc = new ProductDocument(
            null,
            request.name(),
            request.description(),
            request.price(),
            request.sku(),
            request.active() != null ? request.active() : true,
            now,
            now
        );
        ProductDocument saved = productRepository.save(doc);
        return ProductResponse.fromDocument(saved);
    }

    @Override
    public ProductResponse updateProduct(String id, UpdateProductRequest request) {
        ProductDocument doc = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));

        if (request.name() != null) {
            doc.setName(request.name());
        }
        if (request.description() != null) {
            doc.setDescription(request.description());
        }
        if (request.price() != null) {
            doc.setPrice(request.price());
        }
        if (request.active() != null) {
            doc.setActive(request.active());
        }
        doc.setUpdatedAt(Instant.now());

        ProductDocument updated = productRepository.save(doc);
        return ProductResponse.fromDocument(updated);
    }

    @Override
    public void deleteProduct(String id) {
        ProductDocument doc = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
        doc.setActive(false);
        doc.setUpdatedAt(Instant.now());
        productRepository.save(doc);
    }
}
