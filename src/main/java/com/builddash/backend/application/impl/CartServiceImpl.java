package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CartService;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.port.CartLineItemRepository;
import com.builddash.backend.domain.port.CartPricingCalculator;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.domain.port.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartLineItemRepository cartLineItemRepository;
    private final CartPricingCalculator cartPricingCalculator;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public PricedCart getCart(UUID userId, UUID projectId) {
        Cart cart = getOrCreateCart(userId, projectId);
        return cartPricingCalculator.calculate(cart, userId);
    }

    @Override
    @Transactional
    public PricedCart upsertItem(UUID userId, UUID projectId, UUID productId, int quantity, String itemCoupon) {
        if (productRepository.findById(productId).isEmpty()) {
            throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + productId);
        }

        Cart cart = getOrCreateCart(userId, projectId);

        if (quantity <= 0) {
            cartLineItemRepository.deleteByCartIdAndProductId(cart.id(), productId);
        } else {
            CartLineItem lineItem = new CartLineItem(
                    UUID.randomUUID(),
                    cart.id(),
                    productId,
                    quantity,
                    itemCoupon
            );
            cartLineItemRepository.save(lineItem);
        }

        Cart refreshed = cartRepository.findById(cart.id()).orElseThrow();
        return cartPricingCalculator.calculate(refreshed, userId);
    }

    @Override
    @Transactional
    public PricedCart removeItem(UUID userId, UUID projectId, UUID productId) {
        return upsertItem(userId, projectId, productId, 0, null);
    }

    @Override
    @Transactional
    public PricedCart applyCartCoupon(UUID userId, UUID projectId, String couponCode) {
        Cart cart = getOrCreateCart(userId, projectId);
        Cart updated = new Cart(
                cart.id(),
                cart.userId(),
                cart.projectId(),
                couponCode,
                cart.items()
        );
        cartRepository.save(updated);
        Cart refreshed = cartRepository.findById(cart.id()).orElseThrow();
        return cartPricingCalculator.calculate(refreshed, userId);
    }

    @Override
    @Transactional
    public PricedCart removeCartCoupon(UUID userId, UUID projectId) {
        return applyCartCoupon(userId, projectId, null);
    }

    @Override
    @Transactional
    public void clearCart(UUID userId, UUID projectId) {
        Cart cart = getOrCreateCart(userId, projectId);
        cartLineItemRepository.deleteByCartId(cart.id());
        Cart updated = new Cart(cart.id(), cart.userId(), cart.projectId(), null, List.of());
        cartRepository.save(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PricedCart getCartById(UUID userId, UUID cartId) {
        Cart cart = cartRepository.findById(cartId)
                .filter(c -> c.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("CART_NOT_FOUND", "Cart not found: " + cartId));
        return cartPricingCalculator.calculate(cart, userId);
    }

    @Override
    @Transactional
    public PricedCart createReorderCart(UUID userId, List<CartLineItem> items) {
        Cart cart = new Cart(UUID.randomUUID(), userId, UUID.randomUUID(), null, List.of());
        cartRepository.save(cart);

        for (CartLineItem inputItem : items) {
            if (productRepository.findById(inputItem.productId()).isEmpty()) {
                throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + inputItem.productId());
            }
            CartLineItem lineItem = new CartLineItem(
                    UUID.randomUUID(),
                    cart.id(),
                    inputItem.productId(),
                    inputItem.quantity(),
                    null
            );
            cartLineItemRepository.save(lineItem);
        }

        Cart refreshed = cartRepository.findById(cart.id()).orElseThrow();
        return cartPricingCalculator.calculate(refreshed, userId);
    }

    private Cart getOrCreateCart(UUID userId, UUID projectId) {
        return cartRepository.findByUserIdAndProjectId(userId, projectId)
                .orElseGet(() -> {
                    Cart newCart = new Cart(
                            UUID.randomUUID(),
                            userId,
                            projectId,
                            null,
                            new ArrayList<>()
                    );
                    return cartRepository.save(newCart);
                });
    }
}
