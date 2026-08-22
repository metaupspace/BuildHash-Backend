package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.PricedCart;

import java.util.UUID;

public interface CartPricingCalculator {
    PricedCart calculate(Cart cart, UUID userId);
}
