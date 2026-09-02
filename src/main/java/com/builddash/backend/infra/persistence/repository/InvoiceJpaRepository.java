package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.InvoiceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity, UUID> {

    Optional<InvoiceEntity> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InvoiceEntity i WHERE i.id = :id")
    Optional<InvoiceEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT i FROM InvoiceEntity i WHERE (i.status = 'PENDING' AND i.attemptCount < :maxAttempts) OR (i.status = 'GENERATING' AND i.attemptCount < :maxAttempts AND i.updatedAt < :cutoff) ORDER BY i.createdAt ASC")
    List<InvoiceEntity> findSchedulerClaimableInvoices(@Param("maxAttempts") int maxAttempts, @Param("cutoff") Instant cutoff);

    @Query("SELECT i FROM InvoiceEntity i WHERE (i.status = 'DLQ_RETRY' AND i.attemptCount <= :maxDlqAttempts) OR (i.status = 'GENERATING' AND i.attemptCount >= :maxAttempts AND i.attemptCount <= :maxDlqAttempts AND i.updatedAt < :cutoff) ORDER BY i.updatedAt ASC")
    List<InvoiceEntity> findDlqClaimableInvoices(@Param("maxAttempts") int maxAttempts, @Param("maxDlqAttempts") int maxDlqAttempts, @Param("cutoff") Instant cutoff);
}
