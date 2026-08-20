package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.PaymentReference;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {
    PaymentReference initiate(UUID orderId, BigDecimal amount);
}
