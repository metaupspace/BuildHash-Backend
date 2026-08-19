package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.CheckoutIntentResponse;
import com.builddash.backend.domain.model.CheckoutIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CheckoutDtoMapper {

    private final CartDtoMapper cartDtoMapper;

    public CheckoutIntentResponse toResponse(CheckoutIntent intent) {
        if (intent == null) return null;
        return new CheckoutIntentResponse(
                intent.id(),
                intent.userId(),
                intent.cartId(),
                intent.addressId(),
                intent.slotId(),
                intent.slotDate(),
                intent.lockedTotal(),
                intent.expiresAt(),
                cartDtoMapper.toResponse(intent.pricedCart())
        );
    }
}
