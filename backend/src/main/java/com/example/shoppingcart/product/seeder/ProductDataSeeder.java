package com.example.shoppingcart.product.seeder;

import com.example.shoppingcart.product.dto.CreateProductRequest;
import com.example.shoppingcart.product.repository.ProductRepository;
import com.example.shoppingcart.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Populates the product catalog with default items on application startup.
 *
 * <p>Active only under the {@code dev} and {@code docker} Spring profiles so it
 * never runs in production or test contexts. The seeder is idempotent: it checks
 * whether each SKU already exists before inserting, making it safe to restart the
 * application without duplicating products.
 *
 * <p>Resolves tech-debt item <strong>TD-T1-06</strong>: "No Default Catalog
 * Database Seeder."
 */
@Component
@Profile("!test")
public class ProductDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductDataSeeder.class);

    private final ProductService productService;
    private final ProductRepository productRepository;

    public ProductDataSeeder(ProductService productService, ProductRepository productRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        List<SeedProduct> seeds = List.of(
            new SeedProduct("Mechanical Keyboard",
                            "Full-size RGB mechanical gaming keyboard, Cherry MX Red switches",
                            new BigDecimal("79.99"),
                            "SKU-KB-001"),
            new SeedProduct("Wireless Mouse",
                            "Ergonomic wireless optical mouse, 2.4 GHz, 12-month battery life",
                            new BigDecimal("34.99"),
                            "SKU-MS-001"),
            new SeedProduct("27-inch Monitor",
                            "QHD IPS 144 Hz gaming monitor with FreeSync Premium",
                            new BigDecimal("299.99"),
                            "SKU-MON-001"),
            new SeedProduct("Aura Pro Mechanical Keyboard",
                            "Anodized CNC aluminum mechanical keyboard with hot-swappable tactile linear switches",
                            new BigDecimal("149.99"),
                            "SKU-KB-002"),
            new SeedProduct("Studio Reference Display 4K",
                            "32-inch 4K UHD color-calibrated IPS reference display with Thunderbolt connectivity",
                            new BigDecimal("499.99"),
                            "SKU-MON-002"),
            new SeedProduct("Studio Planar Audio Headphones",
                            "Open-back planar magnetic studio headphones with handcrafted walnut acoustic chambers",
                            new BigDecimal("199.99"),
                            "SKU-AUD-001"),
            new SeedProduct("Precision Hardware Desk Mat",
                            "Anodized micro-textured aluminum workspace mat with non-slip ceramic base",
                            new BigDecimal("45.00"),
                            "SKU-PER-001")
        );

        int created = 0;
        for (SeedProduct seed : seeds) {
            if (productRepository.findBySku(seed.sku()).isEmpty()) {
                productService.createProduct(new CreateProductRequest(
                        seed.name(),
                        seed.description(),
                        seed.price(),
                        seed.sku(),
                        true
                ));
                log.info("Seeded product: {} ({})", seed.name(), seed.sku());
                created++;
            } else {
                log.debug("Skipping existing product: {}", seed.sku());
            }
        }
        log.info("ProductDataSeeder complete — {} new product(s) inserted.", created);
    }

    private record SeedProduct(String name, String description, BigDecimal price, String sku) {}
}
