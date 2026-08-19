package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.ApplyCartCouponRequest;
import com.builddash.backend.api.dto.request.UpsertCartItemRequest;
import com.builddash.backend.api.dto.response.PricedCartResponse;
import com.builddash.backend.api.mapper.CartDtoMapper;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.model.PricedCart;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/cart")
@Tag(name = "Cart", description = "Server-authoritative shopping cart with live pricing")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CartDtoMapper cartDtoMapper;

    @GetMapping
    @Operation(summary = "Get user's live-priced cart")
    public PricedCartResponse getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        PricedCart pricedCart = cartService.getCart(user.userId(), null);
        return cartDtoMapper.toResponse(pricedCart);
    }

    @PutMapping("/items")
    @Operation(summary = "Add, update, or remove an item in cart. Quantity 0 removes item.")
    public PricedCartResponse upsertItem(@Valid @RequestBody UpsertCartItemRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        PricedCart pricedCart = cartService.upsertItem(
                user.userId(),
                null,
                request.productId(),
                request.quantity(),
                request.itemCoupon()
        );
        return cartDtoMapper.toResponse(pricedCart);
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove item from cart")
    public PricedCartResponse removeItem(@PathVariable UUID productId,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        PricedCart pricedCart = cartService.removeItem(user.userId(), null, productId);
        return cartDtoMapper.toResponse(pricedCart);
    }

    @PostMapping("/coupon")
    @Operation(summary = "Apply a cart-level coupon")
    public PricedCartResponse applyCoupon(@Valid @RequestBody ApplyCartCouponRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        PricedCart pricedCart = cartService.applyCartCoupon(user.userId(), null, request.couponCode());
        return cartDtoMapper.toResponse(pricedCart);
    }

    @DeleteMapping("/coupon")
    @Operation(summary = "Remove applied cart-level coupon")
    public PricedCartResponse removeCoupon(@AuthenticationPrincipal AuthenticatedUser user) {
        PricedCart pricedCart = cartService.removeCartCoupon(user.userId(), null);
        return cartDtoMapper.toResponse(pricedCart);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear all items and coupons from cart")
    public void clearCart(@AuthenticationPrincipal AuthenticatedUser user) {
        cartService.clearCart(user.userId(), null);
    }
}
