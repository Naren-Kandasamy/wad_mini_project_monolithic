package com.example.shoppingcart;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Module-boundary enforcement tests using ArchUnit.
 *
 * <p>These tests verify that the modular monolith's inter-module
 * dependencies never violate the "no cross-module internal access" rule:
 * only the public {@code api} package of each module may be consumed by
 * another module; the {@code internal} packages are private by convention
 * and enforced here at test time.
 *
 * <p>Runs against production bytecode only ({@code DO_NOT_INCLUDE_TESTS})
 * to prevent test-helper imports from creating false violations.
 */
class ArchitectureTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.shoppingcart");

    // ── Checkout must not reach into other modules' internals ──────────────────

    @Test
    @DisplayName("checkout module must not access cart.internal")
    void checkoutShouldNotAccessCartInternal() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..checkout..")
                .should().dependOnClassesThat().resideInAPackage("..cart.internal..");
        rule.check(importedClasses);
    }

    @Test
    @DisplayName("checkout module must not access order.internal")
    void checkoutShouldNotAccessOrderInternal() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..checkout..")
                .should().dependOnClassesThat().resideInAPackage("..order.internal..");
        rule.check(importedClasses);
    }

    // ── No module may cross into another module's internal layer ──────────────

    @Test
    @DisplayName("cart.internal must not be accessed by product, order, or checkout modules")
    void cartInternalShouldNotBeAccessedCrossModule() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..product..", "..order..", "..checkout..", "..security..", "..shared..")
                .should().dependOnClassesThat().resideInAPackage("..cart.internal..");
        rule.check(importedClasses);
    }

    @Test
    @DisplayName("order.internal must not be accessed by product, cart, or checkout modules")
    void orderInternalShouldNotBeAccessedCrossModule() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..product..", "..cart..", "..checkout..", "..security..", "..shared..")
                .should().dependOnClassesThat().resideInAPackage("..order.internal..");
        rule.check(importedClasses);
    }

    @Test
    @DisplayName("product.internal must not be accessed by cart, order, or checkout modules")
    void productInternalShouldNotBeAccessedCrossModule() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..cart..", "..order..", "..checkout..", "..security..", "..shared..")
                .should().dependOnClassesThat().resideInAPackage("..product.internal..");
        rule.check(importedClasses);
    }

    // ── Controllers must not reach directly into repositories ─────────────────

    @Test
    @DisplayName("REST controllers must not import repository classes directly")
    void controllersShouldNotAccessRepositoriesDirectly() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
        rule.check(importedClasses);
    }
}
