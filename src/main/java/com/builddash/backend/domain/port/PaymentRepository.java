package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findLatestByOrderId(UUID orderId);

    /** DPDP export: the full payment history of an order, not just the latest attempt. */
    List<Payment> findAllByOrderId(UUID orderId);
}
