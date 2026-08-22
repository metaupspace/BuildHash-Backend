package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.CheckoutIntent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface CheckoutIntentService {

    CheckoutIntent createIntent(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal, UUID cartId);
}
