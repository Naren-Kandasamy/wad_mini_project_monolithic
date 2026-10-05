package com.example.shoppingcart.checkout;

import com.example.shoppingcart.cart.api.CartApi;
import com.example.shoppingcart.cart.api.CartItemSnapshot;
import com.example.shoppingcart.cart.api.CheckoutCart;
import com.example.shoppingcart.checkout.dto.CheckoutResponse;
import com.example.shoppingcart.checkout.service.CheckoutService;
import com.example.shoppingcart.order.api.CreateOrderCommand;
import com.example.shoppingcart.order.api.OrderApi;
import com.example.shoppingcart.order.api.OrderSnapshot;
import com.example.shoppingcart.shared.error.CartEmptyException;
import com.example.shoppingcart.shared.error.CartVersionConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceUnitTest {

    @Mock
    private CartApi cartApi;

    @Mock
    private OrderApi orderApi;

    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        checkoutService = new CheckoutService(cartApi, orderApi);
    }

    @Test
    @DisplayName("processCheckout: successfully completes checkout transaction and finalizes cart")
    void processCheckout_success() {
        String userId = "user1";
        String idempotencyKey = "key-001";
        long cartVersion = 2L;

        when(orderApi.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());

        List<CartItemSnapshot> items = List.of(
            new CartItemSnapshot("p1", "Item 1", new BigDecimal("25.00"), 2)
        );
        CheckoutCart cart = new CheckoutCart(userId, cartVersion, items, new BigDecimal("50.00"));
        when(cartApi.loadForCheckout(userId)).thenReturn(cart);

        OrderSnapshot orderSnapshot = new OrderSnapshot(
            "order-100", userId, idempotencyKey, items, new BigDecimal("50.00"), "CONFIRMED", Instant.now()
        );
        when(orderApi.createOrder(any(CreateOrderCommand.class))).thenReturn(orderSnapshot);

        CheckoutResponse response = checkoutService.processCheckout(userId, idempotencyKey);

        assertNotNull(response);
        assertEquals("order-100", response.orderId());
        assertEquals("CONFIRMED", response.status());
        assertEquals(new BigDecimal("50.00"), response.totalAmount());
        verify(cartApi).finalizeCheckout(userId, cartVersion);
    }

    @Test
    @DisplayName("processCheckout: idempotent replay returns existing order without creating new order")
    void processCheckout_idempotentReplay() {
        String userId = "user1";
        String idempotencyKey = "key-replay";
        OrderSnapshot existingOrder = new OrderSnapshot(
            "order-existing", userId, idempotencyKey, List.of(), new BigDecimal("30.00"), "CONFIRMED", Instant.now()
        );

        when(orderApi.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.of(existingOrder));

        CheckoutResponse response = checkoutService.processCheckout(userId, idempotencyKey);

        assertEquals("order-existing", response.orderId());
        verify(cartApi, never()).loadForCheckout(any());
        verify(orderApi, never()).createOrder(any());
        verify(cartApi, never()).finalizeCheckout(any(), anyLong());
    }

    @Test
    @DisplayName("processCheckout: throws CartEmptyException when cart has no items")
    void processCheckout_emptyCartThrowsException() {
        String userId = "user1";
        String idempotencyKey = "key-empty";

        when(orderApi.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cartApi.loadForCheckout(userId)).thenReturn(new CheckoutCart(userId, 1L, List.of(), BigDecimal.ZERO));

        assertThrows(CartEmptyException.class, () ->
            checkoutService.processCheckout(userId, idempotencyKey)
        );

        verify(orderApi, never()).createOrder(any());
    }

    @Test
    @DisplayName("processCheckout: propagates CartVersionConflictException on concurrent cart modification")
    void processCheckout_propagatesVersionConflict() {
        String userId = "user1";
        String idempotencyKey = "key-conflict";
        long cartVersion = 1L;

        when(orderApi.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());

        List<CartItemSnapshot> items = List.of(new CartItemSnapshot("p1", "Item", BigDecimal.TEN, 1));
        when(cartApi.loadForCheckout(userId)).thenReturn(new CheckoutCart(userId, cartVersion, items, BigDecimal.TEN));

        OrderSnapshot orderSnapshot = new OrderSnapshot("ord-1", userId, idempotencyKey, items, BigDecimal.TEN, "CONFIRMED", Instant.now());
        when(orderApi.createOrder(any(CreateOrderCommand.class))).thenReturn(orderSnapshot);

        doThrow(new CartVersionConflictException("Conflict")).when(cartApi).finalizeCheckout(userId, cartVersion);

        assertThrows(CartVersionConflictException.class, () ->
            checkoutService.processCheckout(userId, idempotencyKey)
        );
    }

    @Test
    @DisplayName("processCheckout: concurrent race duplicate key cleanly re-fetches winning order")
    void processCheckout_handlesDuplicateKeyRaceGracefully() {
        String userId = "user1";
        String idempotencyKey = "key-race";

        when(orderApi.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
            .thenReturn(Optional.empty()) // first check in service passes
            .thenReturn(Optional.of(new OrderSnapshot("ord-winner", userId, idempotencyKey, List.of(), BigDecimal.TEN, "CONFIRMED", Instant.now()))); // catch block finds it

        List<CartItemSnapshot> items = List.of(new CartItemSnapshot("p1", "Item", BigDecimal.TEN, 1));
        when(cartApi.loadForCheckout(userId)).thenReturn(new CheckoutCart(userId, 1L, items, BigDecimal.TEN));

        when(orderApi.createOrder(any(CreateOrderCommand.class)))
            .thenThrow(new DuplicateKeyException("E11000 duplicate key error"));

        CheckoutResponse response = checkoutService.processCheckout(userId, idempotencyKey);

        assertNotNull(response);
        assertEquals("ord-winner", response.orderId());
    }
}
