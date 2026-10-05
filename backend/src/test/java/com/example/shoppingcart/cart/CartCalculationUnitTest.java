package com.example.shoppingcart.cart;

import com.example.shoppingcart.cart.dto.AddItemRequest;
import com.example.shoppingcart.cart.dto.CartResponse;
import com.example.shoppingcart.cart.dto.UpdateQuantityRequest;
import com.example.shoppingcart.cart.model.CartDocument;
import com.example.shoppingcart.cart.model.CartItem;
import com.example.shoppingcart.cart.repository.CartRepository;
import com.example.shoppingcart.cart.service.CartServiceImpl;
import com.example.shoppingcart.product.api.ProductApi;
import com.example.shoppingcart.product.api.ProductSnapshot;
import com.example.shoppingcart.shared.error.CartNotFoundException;
import com.example.shoppingcart.shared.error.CartVersionConflictException;
import com.example.shoppingcart.shared.error.ProductNotFoundException;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartCalculationUnitTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductApi productApi;

    @Mock
    private MongoTemplate mongoTemplate;

    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(cartRepository, productApi, mongoTemplate);
    }

    @Test
    @DisplayName("calculateSubtotal: sums item line totals accurately with BigDecimal arithmetic")
    void calculateSubtotal_sumsAccurately() {
        CartItem item1 = new CartItem("p1", "Item 1", new BigDecimal("10.50"), 2);
        CartItem item2 = new CartItem("p2", "Item 2", new BigDecimal("5.25"), 3);
        CartDocument cart = new CartDocument("c1", "user1", 1L, List.of(item1, item2), Instant.now());

        BigDecimal subtotal = cart.calculateSubtotal();
        assertEquals(new BigDecimal("36.75"), subtotal);
    }

    @Test
    @DisplayName("calculateSubtotal: returns zero for empty items")
    void calculateSubtotal_emptyReturnsZero() {
        CartDocument cart = new CartDocument("c1", "user1", 1L, new ArrayList<>(), Instant.now());
        assertEquals(BigDecimal.ZERO, cart.calculateSubtotal());
    }

    @Test
    @DisplayName("addItem: increases quantity and updates price if item already in cart")
    void addItem_incrementsQuantityForExistingItem() {
        ProductSnapshot product = new ProductSnapshot("p1", "Item 1", "Desc", new BigDecimal("12.00"), "SKU-1", true);
        when(productApi.getAvailableProduct("p1")).thenReturn(Optional.of(product));

        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("p1", "Item 1", new BigDecimal("10.00"), 1));
        CartDocument cart = new CartDocument("c1", "user1", 1L, items, Instant.now());
        when(cartRepository.findByUserId("user1")).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(CartDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CartResponse response = cartService.addItem("user1", new AddItemRequest("p1", 2));

        assertEquals(1, response.items().size());
        assertEquals(3, response.items().get(0).quantity());
        assertEquals(new BigDecimal("12.00"), response.items().get(0).unitPrice());
        assertEquals(new BigDecimal("36.00"), response.subtotal());
        assertEquals(2L, response.version());
    }

    @Test
    @DisplayName("addItem: throws ProductNotFoundException if product not available")
    void addItem_throwsWhenProductNotAvailable() {
        when(productApi.getAvailableProduct("p_invalid")).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () ->
            cartService.addItem("user1", new AddItemRequest("p_invalid", 1))
        );
    }

    @Test
    @DisplayName("updateItemQuantity: removes item when quantity is 0 or less")
    void updateItemQuantity_removesItemWhenZero() {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("p1", "Item 1", new BigDecimal("10.00"), 2));
        CartDocument cart = new CartDocument("c1", "user1", 1L, items, Instant.now());
        when(cartRepository.findByUserId("user1")).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(CartDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CartResponse response = cartService.updateItemQuantity("user1", "p1", new UpdateQuantityRequest(0));

        assertTrue(response.items().isEmpty());
        assertEquals(BigDecimal.ZERO, response.subtotal());
    }

    @Test
    @DisplayName("finalizeCheckout: throws CartVersionConflictException when modifiedCount is 0")
    void finalizeCheckout_throwsOnVersionMismatch() {
        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(CartDocument.class)))
            .thenReturn(updateResult);

        assertThrows(CartVersionConflictException.class, () ->
            cartService.finalizeCheckout("user1", 3L)
        );
    }

    @Test
    @DisplayName("finalizeCheckout: completes cleanly when version matches")
    void finalizeCheckout_succeedsWhenVersionMatches() {
        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(CartDocument.class)))
            .thenReturn(updateResult);

        assertDoesNotThrow(() -> cartService.finalizeCheckout("user1", 3L));
    }
}
