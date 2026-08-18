package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.PriceCalculationResult;
import com.builddash.backend.domain.model.PricingRequest;

public interface PricingCalculator {

    PriceCalculationResult calculate(PricingRequest request);
}
