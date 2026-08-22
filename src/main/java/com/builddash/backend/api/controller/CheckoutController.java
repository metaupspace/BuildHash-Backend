package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.CreateCheckoutIntentRequest;
import com.builddash.backend.api.dto.response.CheckoutIntentResponse;
import com.builddash.backend.api.mapper.CheckoutDtoMapper;
import com.builddash.backend.application.service.CheckoutIntentService;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.model.CheckoutIntent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
@Tag(name = "Checkout", description = "Pre-payment validation and slot lock acquisition")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutIntentService checkoutIntentService;
    private final CheckoutDtoMapper checkoutDtoMapper;

    @PostMapping("/intent")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Validate cart, lock delivery slot, and create pre-payment checkout intent")
    public CheckoutIntentResponse createIntent(@Valid @RequestBody CreateCheckoutIntentRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        CheckoutIntent intent = checkoutIntentService.createIntent(
                user.userId(),
                request.addressId(),
                request.slotId(),
                request.slotDate(),
                request.expectedTotal(),
                request.cartId()
        );
        return checkoutDtoMapper.toResponse(intent);
    }
}
