package com.builddash.backend.application.service;

import com.builddash.backend.application.impl.PricingContext;
import com.builddash.backend.domain.model.PriceCalculationResult;

@FunctionalInterface
public interface PricingStep {

    PriceCalculationResult apply(PriceCalculationResult running, PricingContext ctx);
}
