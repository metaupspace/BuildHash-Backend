package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.AddressService;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.CheckoutIntentService;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.exception.CheckoutValidationException;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.CheckoutIntent;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.PricedCart;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutIntentServiceImpl implements CheckoutIntentService {

    private final CartService cartService;
    private final AddressService addressService;
    private final DeliverySlotService deliverySlotService;

    private static final Duration LOCK_TTL = Duration.ofMinutes(15);

    @Override
    @Transactional
    public CheckoutIntent createIntent(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal, UUID cartId) {
        // 1. Verify Cart not empty and re-calculate live pricing
        PricedCart pricedCart;
        if (cartId != null) {
            pricedCart = cartService.getCartById(userId, cartId);
        } else {
            pricedCart = cartService.getCart(userId, null);
        }
        if (pricedCart.items().isEmpty()) {
            throw new CheckoutValidationException("CART_EMPTY", "Cannot initiate checkout with an empty cart");
        }

        // 2. Validate Address
        Address address = addressService.getAddress(addressId);
        if (!address.userId().equals(userId)) {
            throw new CheckoutValidationException("INVALID_ADDRESS", "Address does not belong to the user");
        }
        if (!address.isServiceable()) {
            throw new CheckoutValidationException("ADDRESS_NOT_SERVICEABLE", "Delivery address is outside serviceable area");
        }

        // 3. Price validation check (if client provided expected total)
        if (expectedTotal != null && pricedCart.finalTotal().compareTo(expectedTotal) != 0) {
            throw new CheckoutValidationException("PRICE_CHANGED",
                    String.format("Cart total has changed from %s to %s", expectedTotal, pricedCart.finalTotal()));
        }

        // 4. Acquire / swap delivery slot lock (15-min TTL)
        DeliverySlotLock lock = deliverySlotService.acquireOrSwapLock(userId, slotId, slotDate, LOCK_TTL);

        // 5. Build and return CheckoutIntent
        return new CheckoutIntent(
                UUID.randomUUID(),
                userId,
                pricedCart.id(),
                addressId,
                slotId,
                slotDate,
                pricedCart.finalTotal(),
                lock.expiresAt(),
                lock.id(),
                pricedCart
        );
    }
}
