package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.PaymentReconciliationStatus;
import com.builddash.backend.domain.enums.PaymentReconciliationType;
import com.builddash.backend.infra.persistence.entity.PaymentReconciliationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentReconciliationJpaRepository extends JpaRepository<PaymentReconciliationEntity, UUID> {

    Optional<PaymentReconciliationEntity> findByOrderIdAndReconciliationType(UUID orderId, PaymentReconciliationType reconciliationType);

    List<PaymentReconciliationEntity> findByStatus(PaymentReconciliationStatus status);
}
