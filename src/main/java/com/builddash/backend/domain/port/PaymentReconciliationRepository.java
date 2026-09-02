package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.PaymentReconciliationStatus;
import com.builddash.backend.domain.enums.PaymentReconciliationType;
import com.builddash.backend.domain.model.PaymentReconciliation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentReconciliationRepository {

    PaymentReconciliation save(PaymentReconciliation reconciliation);

    Optional<PaymentReconciliation> findById(UUID id);

    Optional<PaymentReconciliation> findByOrderIdAndType(UUID orderId, PaymentReconciliationType type);

    List<PaymentReconciliation> findByStatus(PaymentReconciliationStatus status);
}
