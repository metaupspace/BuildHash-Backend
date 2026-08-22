package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.AddressService;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.CheckoutIntentService;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.DeliverySlotLockStatus;
import com.builddash.backend.domain.exception.CheckoutValidationException;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.CheckoutIntent;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.model.PricedCartLineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckoutIntentServiceImplTest {

    private CartService cartService;
    private AddressService addressService;
    private DeliverySlotService deliverySlotService;
    private CheckoutIntentService checkoutIntentService;

    @BeforeEach
    void setUp() {
        cartService = mock(CartService.class);
        addressService = mock(AddressService.class);
        deliverySlotService = mock(DeliverySlotService.class);
        checkoutIntentService = new CheckoutIntentServiceImpl(
                cartService,
                addressService,
                deliverySlotService
        );
    }

    @Test
    void createIntent_emptyCart_throwsValidationException() {
        UUID userId = UUID.randomUUID();
        PricedCart emptyCart = new PricedCart(UUID.randomUUID(), userId, null, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        when(cartService.getCart(userId, null)).thenReturn(emptyCart);

        assertThatThrownBy(() -> checkoutIntentService.createIntent(userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), null, null))
                .isInstanceOf(CheckoutValidationException.class)
                .hasMessageContaining("Cannot initiate checkout with an empty cart");
    }

    @Test
    void createIntent_addressNotBelongingToUser_throwsValidationException() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        PricedCartLineItem item = new PricedCartLineItem(UUID.randomUUID(), 1, "123", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null);
        PricedCart cart = new PricedCart(UUID.randomUUID(), userId, null, List.of(item), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null, null);
        when(cartService.getCart(userId, null)).thenReturn(cart);

        Address address = new Address(addressId, otherUserId, "HOME", "123 St", null, "City", "State", "12345", 10.0, 20.0, true);
        when(addressService.getAddress(addressId)).thenReturn(address);

        assertThatThrownBy(() -> checkoutIntentService.createIntent(userId, addressId, UUID.randomUUID(), LocalDate.now(), null, null))
                .isInstanceOf(CheckoutValidationException.class)
                .hasMessageContaining("Address does not belong to the user");
    }

    @Test
    void createIntent_addressNotServiceable_throwsValidationException() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        PricedCartLineItem item = new PricedCartLineItem(UUID.randomUUID(), 1, "123", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null);
        PricedCart cart = new PricedCart(UUID.randomUUID(), userId, null, List.of(item), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null, null);
        when(cartService.getCart(userId, null)).thenReturn(cart);

        Address address = new Address(addressId, userId, "HOME", "123 St", null, "City", "State", "12345", 10.0, 20.0, false);
        when(addressService.getAddress(addressId)).thenReturn(address);

        assertThatThrownBy(() -> checkoutIntentService.createIntent(userId, addressId, UUID.randomUUID(), LocalDate.now(), null, null))
                .isInstanceOf(CheckoutValidationException.class)
                .hasMessageContaining("outside serviceable area");
    }

    @Test
    void createIntent_priceChanged_throwsValidationException() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        PricedCartLineItem item = new PricedCartLineItem(UUID.randomUUID(), 1, "123", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null);
        PricedCart cart = new PricedCart(UUID.randomUUID(), userId, null, List.of(item), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), null, null);
        when(cartService.getCart(userId, null)).thenReturn(cart);

        Address address = new Address(addressId, userId, "HOME", "123 St", null, "City", "State", "12345", 10.0, 20.0, true);
        when(addressService.getAddress(addressId)).thenReturn(address);

        assertThatThrownBy(() -> checkoutIntentService.createIntent(userId, addressId, UUID.randomUUID(), LocalDate.now(), new BigDecimal("90.00"), null))
                .isInstanceOf(CheckoutValidationException.class)
                .hasMessageContaining("Cart total has changed from 90.00 to 100.00");
    }

    @Test
    void createIntent_happyPath_locksSlotAndReturnsIntent() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate slotDate = LocalDate.now().plusDays(1);

        PricedCartLineItem item = new PricedCartLineItem(UUID.randomUUID(), 1, "123", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null);
        PricedCart cart = new PricedCart(UUID.randomUUID(), userId, null, List.of(item), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), null, null);
        when(cartService.getCart(userId, null)).thenReturn(cart);

        Address address = new Address(addressId, userId, "HOME", "123 St", null, "City", "State", "12345", 10.0, 20.0, true);
        when(addressService.getAddress(addressId)).thenReturn(address);

        DeliverySlotLock lock = new DeliverySlotLock(UUID.randomUUID(), userId, slotId, slotDate, Instant.now().plusSeconds(900), DeliverySlotLockStatus.ACTIVE);
        when(deliverySlotService.acquireOrSwapLock(eq(userId), eq(slotId), eq(slotDate), any(Duration.class))).thenReturn(lock);

        CheckoutIntent intent = checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, new BigDecimal("100.00"), null);

        assertThat(intent.userId()).isEqualTo(userId);
        assertThat(intent.slotId()).isEqualTo(slotId);
        assertThat(intent.lockedTotal()).isEqualByComparingTo("100.00");
        assertThat(intent.expiresAt()).isEqualTo(lock.expiresAt());
    }
}
