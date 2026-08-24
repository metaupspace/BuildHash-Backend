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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceImplTest {

    private CartRepository cartRepository;
    private CartLineItemRepository cartLineItemRepository;
    private CartPricingCalculator cartPricingCalculator;
    private ProductRepository productRepository;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        cartLineItemRepository = mock(CartLineItemRepository.class);
        cartPricingCalculator = mock(CartPricingCalculator.class);
        productRepository = mock(ProductRepository.class);
        cartService = new CartServiceImpl(
                cartRepository,
                cartLineItemRepository,
                cartPricingCalculator,
                productRepository
        );
    }

    @Test
    void getCart_whenCartDoesNotExist_createsEmptyCartAndPricesIt() {
        UUID userId = UUID.randomUUID();
        when(cartRepository.findByUserIdAndProjectId(userId, null)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        PricedCart dummyPriced = new PricedCart(UUID.randomUUID(), userId, null, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        when(cartPricingCalculator.calculate(any(Cart.class), eq(userId))).thenReturn(dummyPriced);

        PricedCart result = cartService.getCart(userId, null);

        assertThat(result.userId()).isEqualTo(userId);
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void upsertItem_whenProductMissing_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.upsertItem(userId, null, productId, 2, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void upsertItem_quantityZero_removesItem() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Cart cart = new Cart(cartId, userId, null, com.builddash.backend.domain.enums.CartType.PRIMARY, null, new ArrayList<>());

        when(productRepository.findById(productId)).thenReturn(Optional.of(new com.builddash.backend.domain.model.Product()));
        when(cartRepository.findByUserIdAndProjectId(userId, null)).thenReturn(Optional.of(cart));
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(cartPricingCalculator.calculate(any(), eq(userId))).thenReturn(
                new PricedCart(cartId, userId, null, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null)
        );

        cartService.upsertItem(userId, null, productId, 0, null);

        verify(cartLineItemRepository).deleteByCartIdAndProductId(cartId, productId);
    }

    @Test
    void getCartById_mismatchedOwner_throwsNotFound() {
        UUID cartId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Cart cart = new Cart(cartId, ownerId, null, com.builddash.backend.domain.enums.CartType.PRIMARY, null, List.of());
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.getCartById(otherUserId, cartId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Cart not found");
    }
}
