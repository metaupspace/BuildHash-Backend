package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.infra.persistence.mapper.InvoiceMapper;
import com.builddash.backend.infra.persistence.repository.InvoiceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class InvoiceRepositoryAdapter implements InvoiceRepository {

    private final InvoiceJpaRepository jpaRepository;
    private final InvoiceMapper mapper;

    @Override
    public Invoice save(Invoice invoice) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(invoice)));
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Invoice> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Invoice> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    public List<Invoice> findSchedulerClaimableInvoices(int maxAttempts, Instant cutoff) {
        return jpaRepository.findSchedulerClaimableInvoices(maxAttempts, cutoff).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Invoice> findDlqClaimableInvoices(int maxAttempts, int maxDlqAttempts, Instant cutoff) {
        return jpaRepository.findDlqClaimableInvoices(maxAttempts, maxDlqAttempts, cutoff).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
