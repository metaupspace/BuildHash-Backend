package com.builddash.backend.application.impl;

import com.builddash.backend.domain.model.PriceCalculationResult;

@FunctionalInterface
interface PricingStep {

    PriceCalculationResult apply(PriceCalculationResult running, PricingContext ctx);
}
